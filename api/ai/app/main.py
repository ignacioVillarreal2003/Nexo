import asyncio
import logging
import uuid
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI, Request
from fastapi.exceptions import HTTPException, RequestValidationError
from fastapi.responses import JSONResponse
from prometheus_fastapi_instrumentator import Instrumentator

from . import responses
from .config import Settings, get_settings
from .embeddings import SentenceTransformerEmbedder
from .schemas import AnalyzeRequest, BriefRequest, CompareRequest, EmbedRequest, EmbedResponse
from .security import require_authority
from .service import ContentProcessor

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s - %(message)s")
log = logging.getLogger("ai")

REQUEST_ID = "X-Request-Id"


def build_processor(settings: Settings) -> ContentProcessor:
    embedder = SentenceTransformerEmbedder(settings.embedding_model, settings.embedding_dim)
    llm = None
    if settings.llm_enabled:
        from .llm import GeminiClient

        llm = GeminiClient(settings)
        log.info("Gemini enabled (%s)", settings.gemini_model)
    else:
        log.warning("GEMINI_API_KEY is empty: running in heuristic mode")
    return ContentProcessor(settings, embedder, llm)


@asynccontextmanager
async def lifespan(app: FastAPI):
    if not hasattr(app.state, "processor"):
        app.state.processor = build_processor(get_settings())
        warm_up = getattr(app.state.processor.embedder, "warm_up", None)
        if warm_up:
            # Descarga y carga del modelo en segundo plano para no bloquear el arranque.
            asyncio.get_running_loop().run_in_executor(None, warm_up)
    yield


app = FastAPI(title="Nexo AI API", version="1.0.0", lifespan=lifespan,
              description="Analysis, comparison and embeddings of collected content.")
Instrumentator().instrument(app).expose(app, include_in_schema=False)

analyze_permission = require_authority(get_settings().required_authority)


@app.middleware("http")
async def correlation_id(request: Request, call_next):
    request_id = request.headers.get(REQUEST_ID) or str(uuid.uuid4())
    request.state.request_id = request_id
    response = await call_next(request)
    response.headers[REQUEST_ID] = request_id
    return response


def _error(request: Request, status: int, code: str, message: str) -> JSONResponse:
    """Mismo formato que el ErrorResponse de los servicios Java."""
    return JSONResponse(status_code=status, content={
        "timestamp": responses.ok(None)["timestamp"], "status": status, "code": code, "message": message,
        "path": request.url.path, "requestId": getattr(request.state, "request_id", None)})


@app.exception_handler(HTTPException)
async def http_error(request: Request, exc: HTTPException):
    code = {401: "UNAUTHORIZED", 403: "ACCESS_DENIED", 404: "RESOURCE_NOT_FOUND"}.get(exc.status_code, "BAD_REQUEST")
    return _error(request, exc.status_code, code, str(exc.detail))


@app.exception_handler(RequestValidationError)
async def validation_error(request: Request, exc: RequestValidationError):
    return _error(request, 400, "VALIDATION_FAILED", "The request is not valid")


def processor(request: Request) -> ContentProcessor:
    return request.app.state.processor


@app.get("/actuator/health")
def health(p: ContentProcessor = Depends(processor)):
    if p.llm is None:
        llm = "heuristic"
    else:
        llm = "gemini-cooldown" if p.llm.cooling_down else "gemini"
    return {"status": "UP", "llm": llm}


@app.post("/api/v1/analyses", dependencies=[Depends(analyze_permission)])
async def analyze(req: AnalyzeRequest, request: Request, p: ContentProcessor = Depends(processor)):
    """Relevancia, calidad, título, resumen, palabras clave, entidades y embedding de un contenido."""
    result = await p.analyze(req)
    return responses.ok(result.model_dump(by_alias=True), request_id=request.state.request_id)


@app.post("/api/v1/comparisons", dependencies=[Depends(analyze_permission)])
async def compare(req: CompareRequest, request: Request, p: ContentProcessor = Depends(processor)):
    """Si un contenido nuevo es duplicado, actualización o información nueva respecto de uno anterior."""
    result = await p.compare(req)
    return responses.ok(result.model_dump(by_alias=True, mode="json"), request_id=request.state.request_id)


@app.post("/api/v1/briefs", dependencies=[Depends(analyze_permission)])
async def brief(req: BriefRequest, request: Request, p: ContentProcessor = Depends(processor)):
    """Informe completo de una noticia elegida (se lee la nota entera). 503 si no hay modelo y no se pidió respaldo."""
    result = await p.brief(req)
    if result is None:
        return _error(request, 503, "SERVICE_UNAVAILABLE", "No language model is available right now; retry later")
    return responses.ok(result.model_dump(by_alias=True), request_id=request.state.request_id)


@app.post("/api/v1/embeddings", dependencies=[Depends(analyze_permission)])
async def embed(req: EmbedRequest, request: Request, p: ContentProcessor = Depends(processor)):
    vectors = await p.embed(req.texts)
    body = EmbedResponse(embeddings=vectors, dimension=len(vectors[0]))
    return responses.ok(body.model_dump(by_alias=True), request_id=request.state.request_id)
