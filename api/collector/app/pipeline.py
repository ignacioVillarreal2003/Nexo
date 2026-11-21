"""Ejecución de una recopilación: plan → consulta de fuentes → normalización → extracción → publicación."""

import asyncio
import logging
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone

import httpx

from .clients import EventPublisher, PlatformClient, to_event
from .config import Settings
from .models import Document, Source, Topic
from .sources import FETCHERS, SourceError
from .text import extract_article, is_google_news, resolve_google_news

log = logging.getLogger(__name__)


@dataclass
class RunState:
    run_id: str | None = None
    trigger: str = ""
    started_at: datetime | None = None
    finished_at: datetime | None = None
    status: str = "IDLE"
    topics_planned: int = 0
    topics_done: int = 0
    items_published: int = 0
    source_errors: int = 0
    source_calls: int = 0
    notes: list[str] = field(default_factory=list)


def select_documents(docs: list[Document], max_age_hours: int, limit: int, now: datetime | None = None) -> list[Document]:
    """Descarta lo antiguo y lo repetido dentro de la ejecución; prioriza lo más reciente."""
    now = now or datetime.now(timezone.utc)
    cutoff = now - timedelta(hours=max_age_hours)
    seen: set[str] = set()
    fresh = []
    for d in docs:
        key = d.url.split("#")[0].rstrip("/")
        if key in seen or (d.published_at and d.published_at < cutoff):
            continue
        seen.add(key)
        fresh.append(d)
    fresh.sort(key=lambda d: d.published_at or now, reverse=True)
    return fresh[:limit]


class Collector:

    def __init__(self, settings: Settings, platform: PlatformClient, publisher: EventPublisher):
        self.settings = settings
        self.platform = platform
        self.publisher = publisher
        self.lock = asyncio.Lock()
        self.state = RunState()

    @property
    def running(self) -> bool:
        return self.lock.locked()

    async def run(self, trigger: str, topic_ids: list[str] | None = None) -> RunState:
        if self.lock.locked():
            raise RuntimeError("Ya hay una recopilación en curso")
        async with self.lock:
            self.state = RunState(trigger=trigger, started_at=datetime.now(timezone.utc), status="RUNNING")
            try:
                await self._run(trigger, topic_ids)
            except Exception as exc:  # noqa: BLE001
                log.exception("La recopilación falló")
                self.state.status = "FAILED"
                self.state.notes.append(str(exc))
                if self.state.run_id:
                    await self.platform.report_error(self.state.run_id, "FETCH", None, None, f"Ejecución abortada: {exc}")
            finally:
                self.state.finished_at = datetime.now(timezone.utc)
                if self.state.run_id:
                    try:
                        await self.platform.finish_run(self.state.run_id, self.state.status, self.state.items_published,
                                                       " · ".join(self.state.notes[:5]) or None)
                    except httpx.HTTPError as exc:
                        log.error("No se pudo cerrar la ejecución %s: %s", self.state.run_id, exc)
            return self.state

    async def _run(self, trigger: str, topic_ids: list[str] | None):
        plan = await self.platform.collection_plan(topic_ids)
        if not topic_ids and self.settings.only_subscribed_topics:
            subscribed = await self.platform.subscribed_topic_ids()
            plan = [(t, s) for t, s in plan if t.id in subscribed]
        self.state.topics_planned = len(plan)
        self.state.run_id = await self.platform.start_run(trigger, len(plan))
        log.info("Recopilación %s iniciada (%s): %d tópicos", self.state.run_id, trigger, len(plan))

        limits = httpx.Limits(max_connections=32, max_keepalive_connections=16)
        headers = {"User-Agent": self.settings.user_agent, "Accept-Language": "es,en;q=0.8"}
        async with httpx.AsyncClient(timeout=self.settings.http_timeout_seconds, headers=headers, limits=limits) as http:
            topic_sem = asyncio.Semaphore(self.settings.topic_concurrency)
            extract_sem = asyncio.Semaphore(self.settings.extraction_concurrency)

            async def guarded(topic: Topic, sources: list[Source]):
                async with topic_sem:
                    await self._collect_topic(http, extract_sem, topic, sources)
                    self.state.topics_done += 1

            await asyncio.gather(*(guarded(t, s) for t, s in plan))

        if self.state.source_calls and self.state.source_errors == self.state.source_calls:
            self.state.status = "FAILED"
        elif self.state.source_errors:
            self.state.status = "PARTIAL"
            self.state.notes.append(f"{self.state.source_errors} de {self.state.source_calls} consultas a fuentes fallaron")
        else:
            self.state.status = "COMPLETED"
        log.info("Recopilación %s: %s, %d documentos publicados", self.state.run_id, self.state.status,
                 self.state.items_published)

    async def _collect_topic(self, http: httpx.AsyncClient, extract_sem: asyncio.Semaphore, topic: Topic,
                             sources: list[Source]):
        run_id = self.state.run_id
        gathered: list[tuple[Source, Document]] = []
        for source in sources:
            fetch = FETCHERS.get(source.type)
            if fetch is None:
                await self.platform.report_error(run_id, "FETCH", source.name, topic.id, f"Tipo de fuente no soportado: {source.type}")
                continue
            self.state.source_calls += 1
            try:
                docs = await fetch(http, source, topic, self.settings)
                gathered.extend((source, d) for d in docs)
            except (SourceError, httpx.HTTPError, ValueError, KeyError) as exc:
                self.state.source_errors += 1
                log.warning("Fuente %s falló para %s: %s", source.name, topic.slug, exc)
                await self.platform.report_error(run_id, "FETCH", source.name, topic.id, str(exc))

        by_doc = {id(d): s for s, d in gathered}
        selected = select_documents([d for _, d in gathered], self.settings.max_age_hours, topic.max_items_per_run)
        try:
            known = await self.platform.known_urls([d.url for d in selected])
        except httpx.HTTPError:
            known = set()

        async def enrich_and_publish(doc: Document):
            source = by_doc[id(doc)]
            # Lo ya conocido se publica igual (content lo vincula al tópico) pero sin volver a enriquecerlo.
            fresh = doc.url not in known
            if fresh and not doc.extractable and is_google_news(doc.url):
                # Google News solo trae el titular: se busca la nota original para tener texto e imagen,
                # y la URL real además mejora la deduplicación exacta.
                async with extract_sem:
                    real_url = await resolve_google_news(http, doc.url)
                if real_url:
                    doc.url, doc.extractable = real_url, True
            if fresh and doc.extractable and len(doc.text) < self.settings.min_text_chars:
                async with extract_sem:
                    text, image = await extract_article(http, doc.url)
                if len(text) > len(doc.text):
                    doc.text = text
                doc.image_url = doc.image_url or image
            try:
                await self.publisher.publish(to_event(run_id, topic, source, doc), correlation_id=run_id)
                self.state.items_published += 1
            except Exception as exc:  # noqa: BLE001
                await self.platform.report_error(run_id, "STORE", source.name, topic.id, f"No se pudo publicar: {exc}")

        await asyncio.gather(*(enrich_and_publish(d) for d in selected))
