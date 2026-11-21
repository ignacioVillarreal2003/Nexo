import asyncio
import logging
import uuid
from contextlib import asynccontextmanager

from apscheduler.schedulers.asyncio import AsyncIOScheduler
from apscheduler.triggers.cron import CronTrigger
from fastapi import Depends, FastAPI, Request
from fastapi.exceptions import HTTPException, RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from prometheus_fastapi_instrumentator import Instrumentator
from pydantic import BaseModel

from . import responses
from .clients import EventPublisher, PlatformClient
from .config import get_settings
from .pipeline import Collector
from .security import Principal, require_any_authority, require_authority

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s - %(message)s")
log = logging.getLogger("collector")

REQUEST_ID = "X-Request-Id"


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    platform = PlatformClient(settings)
    publisher = EventPublisher(settings)
    collector = Collector(settings, platform, publisher)
    app.state.collector = collector
    app.state.tasks = set()

    scheduler = AsyncIOScheduler(timezone="UTC")
    if settings.collect_cron:
        async def scheduled():
            if not collector.running:
                await collector.run("SCHEDULED")

        # misfire_grace_time: si el servicio estaba caído a la hora programada, corre al volver (hasta 6 h).
        scheduler.add_job(scheduled, CronTrigger.from_crontab(settings.collect_cron, timezone="UTC"),
                          id="daily-collection", misfire_grace_time=6 * 3600, coalesce=True, max_instances=1)
        scheduler.start()
        log.info("Daily collection scheduled: '%s' (UTC)", settings.collect_cron)
    yield
    if scheduler.running:
        scheduler.shutdown(wait=False)
    await publisher.close()
    await platform.close()


app = FastAPI(title="Nexo Collector API", version="1.0.0", lifespan=lifespan,
              description="Gathers content from the configured sources and publishes it for processing.")
app.add_middleware(
    CORSMiddleware,
    allow_origins=[o.strip() for o in get_settings().cors_allowed_origins.split(",") if o.strip()],
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["*"],
    expose_headers=["X-Correlation-Id", "X-Request-Id"],
    max_age=3600,
)
Instrumentator().instrument(app).expose(app, include_in_schema=False)

trigger_permission = require_any_authority(get_settings().manage_authority, get_settings().request_authority)
read_permission = require_authority(get_settings().read_authority)


@app.middleware("http")
async def correlation_id(request: Request, call_next):
    request_id = request.headers.get(REQUEST_ID) or str(uuid.uuid4())
    request.state.request_id = request_id
    response = await call_next(request)
    response.headers[REQUEST_ID] = request_id
    return response


def _error(request: Request, status: int, code: str, message: str) -> JSONResponse:
    return JSONResponse(status_code=status, content={
        "timestamp": responses.ok(None)["timestamp"], "status": status, "code": code, "message": message,
        "path": request.url.path, "requestId": getattr(request.state, "request_id", None)})


@app.exception_handler(HTTPException)
async def http_error(request: Request, exc: HTTPException):
    code = {401: "UNAUTHORIZED", 403: "ACCESS_DENIED", 404: "RESOURCE_NOT_FOUND", 409: "CONFLICT"}.get(
        exc.status_code, "BAD_REQUEST")
    return _error(request, exc.status_code, code, str(exc.detail))


@app.exception_handler(RequestValidationError)
async def validation_error(request: Request, exc: RequestValidationError):
    return _error(request, 400, "VALIDATION_FAILED", "The request is not valid")


class CollectionRequest(BaseModel):
    topicIds: list[str] | None = None


def collector_of(request: Request) -> Collector:
    return request.app.state.collector


@app.get("/actuator/health")
def health():
    return {"status": "UP"}


@app.post("/api/v1/collections", status_code=202)
async def trigger(request: Request, body: CollectionRequest | None = None,
                  collector: Collector = Depends(collector_of), caller: Principal = Depends(trigger_permission)):
    """Lanza una recopilación en segundo plano; con `topicIds` recopila solo esos tópicos.

    Un administrador (PIPELINE_MANAGE) puede recopilar todo. Un servicio con COLLECTION_REQUEST
    (delivery, para los tópicos recién seguidos) solo puede pedir una lista acotada de tópicos.
    """
    settings = get_settings()
    topic_ids = body.topicIds if body else None
    if settings.manage_authority not in caller.authorities:
        if not topic_ids:
            raise HTTPException(status_code=403, detail="On-demand collections must name their topics")
        if len(topic_ids) > settings.max_requested_topics:
            raise HTTPException(status_code=400, detail=f"At most {settings.max_requested_topics} topics per request")
    if collector.running:
        raise HTTPException(status_code=409, detail="A collection is already running")
    task = asyncio.create_task(collector.run("MANUAL", topic_ids))
    request.app.state.tasks.add(task)
    task.add_done_callback(request.app.state.tasks.discard)
    return responses.ok(None, "Collection started", request.state.request_id)


@app.get("/api/v1/collections/current", dependencies=[Depends(read_permission)])
def current(request: Request, collector: Collector = Depends(collector_of)):
    s = collector.state
    return responses.ok({
        "running": collector.running, "runId": s.run_id, "trigger": s.trigger, "status": s.status,
        "startedAt": s.started_at.isoformat() if s.started_at else None,
        "finishedAt": s.finished_at.isoformat() if s.finished_at else None,
        "topicsPlanned": s.topics_planned, "topicsDone": s.topics_done,
        "itemsPublished": s.items_published, "sourceErrors": s.source_errors,
    }, request_id=request.state.request_id)
