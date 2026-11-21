"""Clientes de los servicios de la plataforma (con token de servicio) y publicación en Kafka."""

import asyncio
import json
import logging
import time
from datetime import datetime

import httpx
from aiokafka import AIOKafkaProducer

from .config import Settings
from .models import Document, Source, Topic

log = logging.getLogger(__name__)

#: Se renueva el token un poco antes de que venza para que ninguna llamada choque con un 401.
TOKEN_REFRESH_SLACK_SECONDS = 30


class ServiceTokenProvider:
    """Token del cliente de servicio del collector (grant client_credentials)."""

    def __init__(self, settings: Settings, http: httpx.AsyncClient):
        self.settings = settings
        self.http = http
        self._token: str | None = None
        self._expires_at = 0.0
        self._lock = asyncio.Lock()

    async def token(self) -> str:
        async with self._lock:
            if self._token and time.monotonic() < self._expires_at:
                return self._token
            response = await self.http.post(
                f"{self.settings.authentication_service_base_url}/api/v1/authentication/token",
                data={"grant_type": "client_credentials",
                      "client_id": self.settings.authentication_service_client_id,
                      "client_secret": self.settings.authentication_service_client_secret})
            response.raise_for_status()
            body = response.json()
            self._token = body["access_token"]
            self._expires_at = time.monotonic() + max(0, int(body.get("expires_in", 0)) - TOKEN_REFRESH_SLACK_SECONDS)
            return self._token

    def invalidate(self) -> None:
        self._token = None


class PlatformClient:
    """Llamadas a topic, subscription y content. Desenvuelven el ApiResponse ({"data": ...})."""

    def __init__(self, settings: Settings):
        self.settings = settings
        self.http = httpx.AsyncClient(timeout=httpx.Timeout(30.0))
        self.tokens = ServiceTokenProvider(settings, self.http)

    async def close(self):
        await self.http.aclose()

    async def _request(self, method: str, url: str, **kwargs):
        for attempt in range(2):
            headers = {"Authorization": f"Bearer {await self.tokens.token()}"}
            response = await self.http.request(method, url, headers=headers, **kwargs)
            if response.status_code == 401 and attempt == 0:
                self.tokens.invalidate()
                continue
            response.raise_for_status()
            return response.json().get("data") if response.content else None
        return None

    async def collection_plan(self, topic_ids: list[str] | None) -> list[tuple[Topic, list[Source]]]:
        params = [("topicIds", t) for t in topic_ids] if topic_ids else None
        data = await self._request("GET", f"{self.settings.topic_service_base_url}/api/v1/collection-plan",
                                   params=params)
        return [(Topic.from_api(item["topic"]), [Source.from_api(s) for s in item["sources"]]) for item in data or []]

    async def subscribed_topic_ids(self) -> set[str]:
        data = await self._request("GET", f"{self.settings.subscription_service_base_url}/api/v1/subscribed-topics")
        return {row["topicId"] for row in data or []}

    async def start_run(self, trigger: str, topics_planned: int) -> str:
        data = await self._request("POST", f"{self.settings.content_service_base_url}/api/v1/pipeline-runs",
                                   json={"trigger": trigger, "topicsPlanned": topics_planned})
        return data["id"]

    async def finish_run(self, run_id: str, status: str, items_collected: int, notes: str | None):
        await self._request("POST", f"{self.settings.content_service_base_url}/api/v1/pipeline-runs/{run_id}/finish",
                            json={"status": status, "itemsCollected": items_collected, "notes": notes})

    async def report_error(self, run_id: str | None, stage: str, source: str | None, topic_id: str | None, message: str):
        try:
            await self._request("POST", f"{self.settings.content_service_base_url}/api/v1/pipeline-errors", json={
                "runId": run_id, "stage": stage, "source": source, "topicId": topic_id, "message": message[:4000]})
        except httpx.HTTPError as exc:
            log.warning("Could not record the pipeline error: %s", exc)

    async def known_urls(self, urls: list[str]) -> set[str]:
        if not urls:
            return set()
        data = await self._request("POST", f"{self.settings.content_service_base_url}/api/v1/contents/known-urls",
                                   json={"urls": urls})
        return set(data or [])


def to_event(run_id: str, topic: Topic, source: Source, doc: Document) -> dict:
    def iso(value: datetime | None):
        return value.isoformat() if value else None

    return {
        "runId": run_id, "topicId": topic.id, "topicName": topic.name, "topicDescription": topic.description,
        "topicKeywords": topic.keywords, "minimumRelevance": topic.min_relevance,
        "sourceId": source.id, "sourceName": doc.source_name or source.name,
        "url": doc.url, "title": doc.title, "text": doc.text, "imageUrl": doc.image_url,
        "author": doc.author, "publishedAt": iso(doc.published_at), "language": doc.language,
    }


class EventPublisher:
    """Publica cada documento en {@code nexo.content.collected}, con la clave del tópico para mantener orden."""

    def __init__(self, settings: Settings):
        self.settings = settings
        self.producer: AIOKafkaProducer | None = None

    async def start(self):
        self.producer = AIOKafkaProducer(bootstrap_servers=self.settings.kafka_bootstrap_servers,
                                         acks="all", enable_idempotence=True)
        await self.producer.start()

    async def publish(self, event: dict, correlation_id: str | None = None):
        if self.producer is None:
            await self.start()
        headers = [("X-Request-Id", correlation_id.encode())] if correlation_id else None
        await self.producer.send_and_wait(self.settings.content_collected_topic,
                                          value=json.dumps(event, ensure_ascii=False).encode("utf-8"),
                                          key=str(event["topicId"]).encode(), headers=headers)

    async def close(self):
        if self.producer is not None:
            await self.producer.stop()
