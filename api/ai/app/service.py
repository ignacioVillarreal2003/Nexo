"""Orquestación: Gemini cuando está disponible, heurísticas como respaldo; embeddings siempre locales."""

import logging

from starlette.concurrency import run_in_threadpool

from . import heuristics
from .article import read_article
from .config import Settings
from .embeddings import Embedder, cosine
from .schemas import (AnalyzeRequest, AnalyzeResponse, BriefRequest, BriefResponse, CompareRequest,
                      CompareResponse, LlmAnalysis, TopicContext, Verdict)

log = logging.getLogger(__name__)


def topic_text(topic: TopicContext) -> str:
    return f"{topic.name}. {topic.description or ''} {' '.join(topic.keywords)}".strip()


def normalize_paragraphs(text: str | None) -> str:
    """Párrafos separados por una línea en blanco, sin espacios sobrantes."""
    if not text:
        return ""
    paragraphs = [" ".join(p.split()) for p in text.replace("\r", "").split("\n")]
    return "\n\n".join(p for p in paragraphs if p)


def clean_points(points: list[str]) -> list[str]:
    cleaned = [p.strip().lstrip("-•*·").strip() for p in points or []]
    return [p for p in cleaned if p][:6]


def reading_minutes(text: str) -> int:
    """Minutos de lectura de la nota original (unas 200 palabras por minuto), al menos 1."""
    return max(1, round(len(text.split()) / 200))


class ContentProcessor:

    def __init__(self, settings: Settings, embedder: Embedder, llm=None):
        self.settings = settings
        self.embedder = embedder
        self.llm = llm

    async def embed(self, texts: list[str]) -> list[list[float]]:
        return await run_in_threadpool(self.embedder.embed, texts)

    async def analyze(self, req: AnalyzeRequest) -> AnalyzeResponse:
        text = (req.text or "").strip()
        source_language = req.language or heuristics.detect_language(f"{req.title} {text}")
        output_language = self.settings.summary_language

        analysis: LlmAnalysis | None = None
        mode = "HEURISTIC"
        if self.llm is not None:
            try:
                analysis = await self.llm.analyze(req.topic, req.title, text, output_language)
                mode = "GEMINI"
            except Exception as exc:  # noqa: BLE001 - cualquier fallo del LLM degrada a heurísticas
                log.warning("Gemini no disponible, se usan heurísticas: %s", exc)
                mode = "HEURISTIC_FALLBACK"

        if analysis is not None:
            title = analysis.title.strip() or heuristics.clean_title(req.title)
            summary = analysis.summary.strip() or heuristics.extractive_summary(req.title, text)
            vectors = await self.embed([f"{title}. {summary}"])
            return AnalyzeResponse(
                relevant=analysis.relevant, relevance_score=round(analysis.relevance_score, 3),
                quality_score=round(analysis.quality_score, 3), title=title, summary=summary,
                title_en=(analysis.title_en or "").strip() or None, summary_en=(analysis.summary_en or "").strip() or None,
                keywords=analysis.keywords[:8], entities=analysis.entities[:8], language=output_language,
                mode=mode, reason=analysis.reason, embedding=vectors[0])

        # ---- Modo heurístico: relevancia por similitud semántica tópico↔contenido.
        title = heuristics.clean_title(req.title)
        summary = heuristics.extractive_summary(req.title, text)
        content_vec, topic_vec = await self.embed([f"{title}. {summary}", topic_text(req.topic)])
        similarity = cosine(content_vec, topic_vec)
        hits = heuristics.keyword_hits(req.topic.keywords + [req.topic.name], req.title, text)
        relevance = heuristics.relevance_score(similarity, hits)
        return AnalyzeResponse(
            relevant=relevance >= 0.5, relevance_score=relevance,
            quality_score=heuristics.quality_score(req.title, text), title=title, summary=summary,
            keywords=heuristics.keywords(req.title, text), entities=heuristics.entities(f"{req.title}. {text}"),
            language=source_language, mode=mode,
            reason=f"Similitud con el tópico {similarity:.2f}, {hits} palabras clave",
            embedding=content_vec)

    async def brief(self, req: BriefRequest, reader=read_article) -> BriefResponse | None:
        """Informe completo de una noticia elegida: se lee la nota entera y la IA escribe el resumen completo,
        los puntos clave y por qué importa, en español y en inglés.

        None si no hay modelo y no se permite el respaldo: quien llama reintenta más tarde.
        """
        text = (req.text or "").strip()
        resolved_url, image_url = None, None
        if req.url and len(text) < self.settings.brief_min_text_chars:
            article = await reader(req.url)
            resolved_url = article.url if article.url != req.url else None
            image_url = article.image_url
            if len(article.text) > len(text):
                text = article.text
        published = req.published_at.date().isoformat() if req.published_at else None

        if self.llm is not None:
            try:
                b = await self.llm.brief(req.title, text, req.source_name, req.topic_name, published)
                detailed = normalize_paragraphs(b.detailed_summary)
                if detailed:
                    detailed_en = normalize_paragraphs(b.detailed_summary_en) or None
                    return BriefResponse(
                        title=b.title.strip() or heuristics.clean_title(req.title), title_en=b.title_en.strip() or None,
                        summary=b.summary.strip() or heuristics.extractive_summary(req.title, text),
                        summary_en=b.summary_en.strip() or None,
                        detailed_summary=detailed, detailed_summary_en=detailed_en,
                        key_points=clean_points(b.key_points), key_points_en=clean_points(b.key_points_en),
                        why_it_matters=b.why_it_matters.strip() or None,
                        why_it_matters_en=b.why_it_matters_en.strip() or None,
                        reading_minutes=reading_minutes(text), source_chars=len(text),
                        resolved_url=resolved_url, image_url=image_url, mode="GEMINI")
            except Exception as exc:  # noqa: BLE001
                log.warning("Gemini no disponible para el informe: %s", exc)
        if not req.allow_fallback:
            return None

        # ---- Respaldo sin LLM: oraciones del propio texto, sin traducción.
        detailed, points = heuristics.extractive_brief(req.title, text)
        return BriefResponse(
            title=heuristics.clean_title(req.title), summary=heuristics.extractive_summary(req.title, text),
            detailed_summary=detailed, key_points=points, reading_minutes=reading_minutes(text),
            source_chars=len(text), resolved_url=resolved_url, image_url=image_url, mode="HEURISTIC")

    async def compare(self, req: CompareRequest) -> CompareResponse:
        if self.llm is not None:
            try:
                result = await self.llm.compare(req.previous, req.candidate)
                return CompareResponse(verdict=result.verdict, reason=result.reason, mode="GEMINI")
            except Exception as exc:  # noqa: BLE001
                log.warning("Gemini no disponible para comparar: %s", exc)
        a, b = await self.embed([f"{req.previous.title}. {req.previous.summary}",
                                 f"{req.candidate.title}. {req.candidate.summary}"])
        verdict, reason = heuristics.compare(req.previous.title, req.previous.published_at,
                                             req.candidate.title, req.candidate.published_at, cosine(a, b))
        return CompareResponse(verdict=Verdict(verdict), reason=reason, mode="HEURISTIC")
