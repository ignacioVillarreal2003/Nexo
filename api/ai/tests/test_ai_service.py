"""Pruebas sin modelos reales: un embedder determinista por bolsa de palabras reemplaza a sentence-transformers."""

import asyncio
import hashlib
import math
from datetime import datetime, timedelta, timezone

import pytest
from fastapi.testclient import TestClient

from app import heuristics
from app.config import Settings, get_settings
from app.security import Principal
from app.main import analyze_permission, app
from app.schemas import LlmAnalysis, LlmComparison, Verdict
from app.service import ContentProcessor


class BagOfWordsEmbedder:
    dimension = 64

    def embed(self, texts):
        out = []
        for text in texts:
            vec = [0.0] * self.dimension
            for word in heuristics.fold(text).split():
                if len(word) > 3 and word not in heuristics.ALL_STOPWORDS:
                    vec[int(hashlib.md5(word.encode()).hexdigest(), 16) % self.dimension] += 1.0
            norm = math.sqrt(sum(v * v for v in vec)) or 1.0
            out.append([v / norm for v in vec])
        return out


class FakeLlm:
    def __init__(self, fail=False):
        self.fail = fail

    async def analyze(self, topic, title, text, language):
        if self.fail:
            raise RuntimeError("cuota agotada")
        return LlmAnalysis(relevant=True, relevance_score=0.9, quality_score=0.8, title="Título IA",
                           summary="Resumen generado.", title_en="AI title", summary_en="Generated summary.",
                           keywords=["ia"], entities=["OpenAI"], reason="central")

    async def compare(self, previous, candidate):
        return LlmComparison(verdict=Verdict.UPDATE, reason="nuevos datos")


SETTINGS = Settings(summary_language="es")
SERVICE = Principal(subject="content", service=True, authorities=frozenset({"AI_ANALYZE"}))


@pytest.fixture
def client():
    def make(llm=None, authenticated=True):
        app.state.processor = ContentProcessor(SETTINGS, BagOfWordsEmbedder(), llm)
        app.dependency_overrides[get_settings] = lambda: SETTINGS
        if authenticated:
            app.dependency_overrides[analyze_permission] = lambda: SERVICE
        return TestClient(app)
    yield make
    app.dependency_overrides.clear()


ARTICLE = {
    "topic": {"name": "Astronomía", "description": "Telescopios, exoplanetas y exploración espacial",
              "keywords": ["telescopio", "exoplaneta", "NASA"]},
    "title": "El telescopio James Webb detecta agua en un exoplaneta templado - El Diario",
    "text": ("Un equipo internacional de astrónomos anunció que el telescopio espacial James Webb detectó vapor de "
             "agua en la atmósfera del exoplaneta K2-18b. El hallazgo fue publicado en una revista científica. "
             "Los investigadores de la NASA planean nuevas observaciones del exoplaneta durante el próximo año."),
}


def test_requires_a_bearer_token(client):
    response = client(authenticated=False).post("/api/v1/analyses", json=ARTICLE)
    assert response.status_code == 401
    assert response.json()["code"] == "UNAUTHORIZED"


def test_heuristic_analysis_is_extractive_and_relevant(client):
    res = client().post("/api/v1/analyses", json=ARTICLE)
    assert res.status_code == 200
    assert res.json()["success"] is True
    body = res.json()["data"]
    assert body["mode"] == "HEURISTIC"
    assert body["relevant"] is True
    assert body["title"] == "El telescopio James Webb detecta agua en un exoplaneta templado"
    # Extractivo: cada oración del resumen aparece en el texto original.
    for sentence in heuristics.sentences(body["summary"]):
        assert sentence.rstrip(".") in ARTICLE["text"]
    assert len(body["embedding"]) == BagOfWordsEmbedder.dimension
    assert "relevanceScore" in body and "qualityScore" in body


def test_irrelevant_content_scores_low(client):
    off_topic = dict(ARTICLE, title="La inflación de octubre sorprendió a los analistas",
                     text="Los precios al consumidor subieron más de lo esperado según el instituto de estadística.")
    body = client().post("/api/v1/analyses", json=off_topic).json()["data"]
    assert body["relevant"] is False
    assert body["relevanceScore"] < 0.5


def test_uses_llm_when_available(client):
    body = client(FakeLlm()).post("/api/v1/analyses", json=ARTICLE).json()["data"]
    assert body["mode"] == "GEMINI"
    assert body["title"] == "Título IA"
    assert body["titleEn"] == "AI title" and body["summaryEn"] == "Generated summary."


def test_falls_back_when_llm_fails(client):
    body = client(FakeLlm(fail=True)).post("/api/v1/analyses", json=ARTICLE).json()["data"]
    assert body["mode"] == "HEURISTIC_FALLBACK"
    assert body["summary"]


def test_compare_endpoint(client):
    now = datetime.now(timezone.utc)
    payload = {
        "previous": {"title": "Terremoto de magnitud 6 sacude la costa", "summary": "Un sismo afectó la región.",
                     "publishedAt": (now - timedelta(hours=30)).isoformat()},
        "candidate": {"title": "Ascienden a 40 las víctimas del terremoto", "summary": "Las autoridades actualizaron el balance.",
                      "publishedAt": now.isoformat()},
    }
    assert client(FakeLlm()).post("/api/v1/comparisons", json=payload).json()["data"]["verdict"] == "UPDATE"
    heuristic = client().post("/api/v1/comparisons", json=payload).json()["data"]
    assert heuristic["verdict"] == "UPDATE"
    assert heuristic["mode"] == "HEURISTIC"


def test_clean_title_and_language():
    assert heuristics.clean_title("Apple presenta su nuevo chip - Xataka") == "Apple presenta su nuevo chip"
    assert heuristics.detect_language("The new model was released by the company and it is fast") == "en"
    assert heuristics.detect_language("El nuevo modelo fue presentado por la empresa y es muy rápido") == "es"


def test_rate_limiter_spaces_requests():
    import asyncio
    import time

    from app.llm import RateLimiter

    async def run():
        limiter = RateLimiter(per_minute=600)  # un pedido cada 0,1 s
        start = time.monotonic()
        for _ in range(3):
            await limiter.acquire()
        return time.monotonic() - start

    assert asyncio.run(run()) >= 0.19


def test_quota_error_pauses_only_that_model_and_degrades_when_none_left():
    import asyncio

    import pytest
    from google.genai import errors

    from app.config import Settings
    from app.llm import GeminiClient, LlmUnavailable

    client = GeminiClient(Settings(gemini_api_key="test", gemini_model="a", gemini_fallback_models="b",
                                   llm_requests_per_minute=6000))
    tried = []

    async def generate(model, *_args, **_kwargs):
        tried.append(model)
        if model == "a":
            raise errors.APIError(429, {"error": {"code": 429, "message": "quota"}})
        return "ok"

    client._generate = generate
    assert asyncio.run(client._call("s", "p", dict)) == "ok"
    assert client.available_models() == ["b"]  # "a" queda en pausa, "b" sigue
    assert not client.cooling_down
    assert asyncio.run(client._call("s", "p", dict)) == "ok"
    assert tried == ["a", "b", "b"]

    async def all_exhausted(model, *_args, **_kwargs):
        raise errors.APIError(429, {"error": {"code": 429, "message": "quota"}})

    client._generate = all_exhausted
    with pytest.raises(LlmUnavailable):
        asyncio.run(client._call("s", "p", dict))
    assert client.cooling_down


def test_transient_error_falls_back_to_next_model():
    import asyncio

    from google.genai import errors

    from app.config import Settings
    from app.llm import GeminiClient

    client = GeminiClient(Settings(gemini_api_key="test", gemini_model="primary",
                                   gemini_fallback_models="secondary", llm_requests_per_minute=6000))
    tried = []

    async def generate(model, *_args, **_kwargs):
        tried.append(model)
        if model == "primary":
            raise errors.APIError(503, {"error": {"code": 503, "message": "high demand"}})
        return "ok"

    client._generate = generate
    assert asyncio.run(client._call("s", "p", dict)) == "ok"
    assert tried == ["primary", "secondary"]
    assert not client.cooling_down


def test_all_models_down_opens_short_cooldown():
    import asyncio

    import pytest

    from app.config import Settings
    from app.llm import GeminiClient, LlmUnavailable

    client = GeminiClient(Settings(gemini_api_key="test", gemini_model="a", gemini_fallback_models="b",
                                   llm_requests_per_minute=6000))

    async def hang(*_args, **_kwargs):
        raise asyncio.TimeoutError

    client._generate = hang
    with pytest.raises(LlmUnavailable):
        asyncio.run(client._call("s", "p", dict))
    assert client.cooling_down


def test_retired_model_is_skipped_and_chain_continues():
    import asyncio

    from google.genai import errors

    from app.config import Settings
    from app.llm import GeminiClient

    client = GeminiClient(Settings(gemini_api_key="test", gemini_model="gone", gemini_fallback_models="ok",
                                   llm_requests_per_minute=6000))

    async def generate(model, *_args, **_kwargs):
        if model == "gone":
            raise errors.APIError(404, {"error": {"code": 404, "message": "no longer available"}})
        return "ok"

    client._generate = generate
    assert asyncio.run(client._call("s", "p", dict)) == "ok"
    assert client.available_models() == ["ok"]


LONG_TEXT = " ".join(
    f"El gobierno de Uruguay anunció el plan número {i} para la red eléctrica con una inversión de {i * 10} millones."
    for i in range(1, 40))


def test_brief_reads_the_article_when_the_text_is_short_and_writes_both_languages():
    from app.article import Article
    from app.schemas import BriefRequest, LlmBrief

    class Writer(FakeLlm):
        def __init__(self):
            super().__init__()
            self.received = ""

        async def brief(self, title, text, source, topic, published):
            self.received = text
            return LlmBrief(title="Plan eléctrico", title_en="Power plan", summary="Breve.", summary_en="Short.",
                            detailed_summary="Primer párrafo.\n\n\nSegundo   párrafo.",
                            detailed_summary_en="First.\n\nSecond.",
                            key_points=["- Punto uno", "", "Punto dos"], key_points_en=["One", "Two"],
                            why_it_matters="Importa.", why_it_matters_en="It matters.")

    async def reader(url):
        return Article(url="https://medio.uy/nota", text=LONG_TEXT, image_url="https://medio.uy/foto.jpg")

    writer = Writer()
    processor = ContentProcessor(SETTINGS, BagOfWordsEmbedder(), writer)
    req = BriefRequest(title="Plan", text="copete corto", url="https://news.google.com/rss/articles/x")
    result = asyncio.run(processor.brief(req, reader=reader))

    assert writer.received == LONG_TEXT  # se usó la nota entera, no el copete
    assert result.mode == "GEMINI" and result.title_en == "Power plan"
    assert result.detailed_summary == "Primer párrafo.\n\nSegundo párrafo."
    assert result.key_points == ["Punto uno", "Punto dos"]
    assert result.resolved_url == "https://medio.uy/nota" and result.image_url == "https://medio.uy/foto.jpg"
    assert result.reading_minutes >= 2 and result.source_chars == len(LONG_TEXT)


def test_brief_without_model_is_503_unless_the_fallback_is_allowed(client):
    body = {"title": "Plan eléctrico", "text": LONG_TEXT}
    assert client(None).post("/api/v1/briefs", json=body).status_code == 503
    data = client(None).post("/api/v1/briefs", json={**body, "allowFallback": True}).json()["data"]
    assert data["mode"] == "HEURISTIC"
    assert "\n\n" in data["detailedSummary"] and data["keyPoints"]
    assert data["detailedSummaryEn"] is None
