"""Contratos REST del servicio. Se exponen en camelCase para coincidir con los records de Java."""

from datetime import datetime
from enum import Enum

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


class CamelModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class TopicContext(CamelModel):
    name: str
    description: str | None = None
    keywords: list[str] = Field(default_factory=list)


class AnalyzeRequest(CamelModel):
    topic: TopicContext
    title: str
    text: str = ""
    url: str | None = None
    published_at: datetime | None = None
    language: str | None = None


class AnalyzeResponse(CamelModel):
    relevant: bool
    relevance_score: float
    quality_score: float
    title: str
    summary: str
    # La misma noticia en inglés, para mostrarla en el idioma del lector. None si no se pudo generar
    # (por ejemplo, en modo heurístico): el lector ve la versión principal.
    title_en: str | None = None
    summary_en: str | None = None
    keywords: list[str]
    entities: list[str]
    language: str
    mode: str
    reason: str | None = None
    embedding: list[float]


class ComparedItem(CamelModel):
    title: str
    summary: str
    published_at: datetime | None = None


class CompareRequest(CamelModel):
    previous: ComparedItem
    candidate: ComparedItem


class Verdict(str, Enum):
    DUPLICATE = "DUPLICATE"
    UPDATE = "UPDATE"
    NEW = "NEW"


class CompareResponse(CamelModel):
    verdict: Verdict
    reason: str
    mode: str


class BriefRequest(CamelModel):
    """Una noticia elegida para entregar: se lee entera y se escribe su informe completo."""
    title: str
    text: str = ""
    url: str | None = None
    source_name: str | None = None
    topic_name: str | None = None
    published_at: datetime | None = None
    # Sin modelo disponible: True arma un informe extractivo con el propio texto; False responde 503.
    allow_fallback: bool = False


class BriefResponse(CamelModel):
    title: str
    title_en: str | None = None
    summary: str
    summary_en: str | None = None
    detailed_summary: str
    detailed_summary_en: str | None = None
    key_points: list[str]
    key_points_en: list[str] = Field(default_factory=list)
    why_it_matters: str | None = None
    why_it_matters_en: str | None = None
    reading_minutes: int
    # Cuánto texto de la nota se pudo leer (para saber si el informe se basó solo en el copete).
    source_chars: int
    resolved_url: str | None = None
    image_url: str | None = None
    mode: str


class EmbedRequest(CamelModel):
    texts: list[str] = Field(min_length=1, max_length=128)


class EmbedResponse(CamelModel):
    embeddings: list[list[float]]
    dimension: int


# ---- Esquemas de salida estructurada que se piden al LLM (snake_case, internos) ----

class LlmAnalysis(BaseModel):
    relevant: bool = Field(description="True si el contenido trata realmente sobre el tópico")
    relevance_score: float = Field(ge=0, le=1, description="0 = nada relacionado, 1 = el tema central es el tópico")
    quality_score: float = Field(ge=0, le=1, description="Calidad informativa: sustancia, claridad, fuente fiable")
    title: str = Field(description="Título descriptivo y neutral, sin clickbait")
    summary: str = Field(description="Resumen fiel de 2 a 4 oraciones")
    title_en: str = Field(description="El mismo título traducido al inglés")
    summary_en: str = Field(description="El mismo resumen traducido al inglés")
    keywords: list[str] = Field(description="3 a 8 conceptos clave")
    entities: list[str] = Field(description="Personas, organizaciones, lugares o productos mencionados")
    reason: str = Field(description="Justificación breve de la relevancia")


class LlmBrief(BaseModel):
    title: str = Field(description="Título descriptivo y neutral en español")
    title_en: str = Field(description="El mismo título en inglés")
    summary: str = Field(description="Resumen breve en español: 2 a 3 oraciones")
    summary_en: str = Field(description="El mismo resumen breve en inglés")
    detailed_summary: str = Field(description="Resumen completo en español, en párrafos separados por una línea en blanco")
    detailed_summary_en: str = Field(description="El mismo resumen completo en inglés")
    key_points: list[str] = Field(description="3 a 6 puntos clave en español, una oración cada uno")
    key_points_en: list[str] = Field(description="Los mismos puntos clave en inglés, en el mismo orden")
    why_it_matters: str = Field(description="Por qué importa, en español: 1 a 2 oraciones. Vacío si el texto no lo permite")
    why_it_matters_en: str = Field(description="Lo mismo en inglés")


class LlmComparison(BaseModel):
    verdict: Verdict
    reason: str
