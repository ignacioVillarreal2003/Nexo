import asyncio
from datetime import datetime, timedelta, timezone

import httpx

from app.config import Settings
from app.models import Document, Source, Topic
from app.pipeline import Collector, select_documents
from app.sources import fetch_rss_search, parse_feed, search_query

TOPIC = Topic(id="t1", slug="astronomia", name="Astronomía", description="Espacio", keywords=["NASA", "telescopio espacial"],
              search_query=None, min_relevance=0.5, max_items_per_run=10, arxiv_categories=None)

RSS = b"""<?xml version="1.0"?>
<rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/"><channel><title>Feed</title>
<item><title>Webb observa una galaxia lejana</title><link>https://example.com/a</link>
<description>&lt;p&gt;Las observaciones revelan estrellas jovenes.&lt;/p&gt;</description>
<pubDate>Fri, 06 Nov 2026 10:00:00 GMT</pubDate><media:content url="https://example.com/a.jpg" medium="image"/></item>
<item><title>Sin enlace</title></item>
</channel></rss>"""

GOOGLE_NEWS = b"""<?xml version="1.0"?>
<rss version="2.0"><channel><title>Google News</title>
<item><title>La NASA lanza una sonda - El Diario</title><link>https://news.google.com/rss/articles/abc</link>
<description>&lt;a href="https://news.google.com/rss/articles/abc"&gt;La NASA lanza una sonda - El Diario&lt;/a&gt;</description>
<source url="https://eldiario.example">El Diario</source><pubDate>Fri, 06 Nov 2026 09:00:00 GMT</pubDate></item>
</channel></rss>"""


def source(type_="RSS", url="https://example.com/feed", **config):
    return Source(id="s1", name="Fuente", type=type_, url=url, configuration=config)


def test_parse_feed_normalizes_entries():
    docs = parse_feed(RSS, source(maxItems=10))
    assert len(docs) == 1
    d = docs[0]
    assert d.url == "https://example.com/a"
    assert d.text == "Las observaciones revelan estrellas jovenes."
    assert d.image_url == "https://example.com/a.jpg"
    assert d.published_at == datetime(2026, 11, 6, 10, tzinfo=timezone.utc)


def test_search_query_quotes_multiword_keywords():
    assert search_query(TOPIC) == 'NASA OR "telescopio espacial"'


def test_google_news_items_keep_publisher_and_skip_extraction():
    def handler(request: httpx.Request):
        assert "q=NASA+OR+%22telescopio+espacial%22" in str(request.url)
        return httpx.Response(200, content=GOOGLE_NEWS)

    async def run():
        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
            src = source("RSS_SEARCH", "https://news.google.com/rss/search?q={query}&hl=es")
            return await fetch_rss_search(client, src, TOPIC, Settings())

    docs = asyncio.run(run())
    assert docs[0].source_name == "El Diario · vía Fuente"
    assert docs[0].extractable is False
    assert docs[0].text == ""


def test_select_documents_drops_old_and_repeated():
    now = datetime(2026, 11, 7, tzinfo=timezone.utc)
    docs = [
        Document(url="https://x.com/1", title="a", published_at=now - timedelta(hours=1)),
        Document(url="https://x.com/1/", title="a bis", published_at=now - timedelta(hours=2)),
        Document(url="https://x.com/2", title="b", published_at=now - timedelta(days=10)),
        Document(url="https://x.com/3", title="c", published_at=None),
    ]
    picked = select_documents(docs, max_age_hours=72, limit=10, now=now)
    assert [d.url for d in picked] == ["https://x.com/3", "https://x.com/1"]


class FakePlatform:
    def __init__(self):
        self.errors, self.finished = [], None

    async def collection_plan(self, topic_ids):
        return [(TOPIC, [source(maxItems=5), source("UNKNOWN")])]

    async def subscribed_topic_ids(self):
        return {"t1"}

    async def start_run(self, trigger, n):
        return "run-1"

    async def finish_run(self, run_id, status, items, notes):
        self.finished = (status, items)

    async def report_error(self, run_id, stage, source_name, topic_id, message):
        self.errors.append(message)

    async def known_urls(self, urls):
        return set(urls)  # todo conocido: no se intenta extraer


class FakePublisher:
    def __init__(self):
        self.events = []

    async def publish(self, event, correlation_id=None):
        self.events.append(event)


def test_collector_publishes_events_and_reports_unknown_sources(monkeypatch):
    import app.sources as sources

    async def fake_rss(client, src, topic, settings):
        return [Document(url="https://example.com/a", title="Hola", published_at=datetime.now(timezone.utc))]

    monkeypatch.setitem(sources.FETCHERS, "RSS", fake_rss)
    platform, publisher = FakePlatform(), FakePublisher()
    state = asyncio.run(Collector(Settings(), platform, publisher).run("MANUAL"))

    assert state.status == "COMPLETED"
    assert platform.finished == ("COMPLETED", 1)
    assert publisher.events[0]["topicId"] == "t1" and publisher.events[0]["runId"] == "run-1"
    assert any("UNKNOWN" in e for e in platform.errors)


def test_cors_allows_only_the_frontend_origin():
    from fastapi.testclient import TestClient

    from app.main import app

    client = TestClient(app)
    preflight = {"Access-Control-Request-Method": "POST", "Access-Control-Request-Headers": "authorization,x-request-id"}
    allowed = client.options("/api/v1/collections", headers={"Origin": "http://localhost:4300", **preflight})
    assert allowed.status_code == 200
    assert allowed.headers["access-control-allow-origin"] == "http://localhost:4300"
    denied = client.options("/api/v1/collections", headers={"Origin": "http://evil.test", **preflight})
    assert "access-control-allow-origin" not in denied.headers


def test_google_news_links_are_detected():
    from app.text import is_google_news

    assert is_google_news("https://news.google.com/rss/articles/CBMiabc?oc=5")
    assert not is_google_news("https://news.google.com/topstories")
    assert not is_google_news("https://www.example.com/rss/articles/x")


def test_unresolvable_google_news_link_returns_none():
    import asyncio

    import httpx

    from app.text import resolve_google_news

    transport = httpx.MockTransport(lambda request: httpx.Response(200, text="<html>sin firma</html>"))

    async def run():
        async with httpx.AsyncClient(transport=transport) as client:
            return await resolve_google_news(client, "https://news.google.com/rss/articles/CBMiabc")

    assert asyncio.run(run()) is None


def _client_as(authorities, running=False):
    from fastapi.testclient import TestClient

    from app.main import app, collector_of, trigger_permission
    from app.security import Principal

    class FakeCollector:
        def __init__(self):
            self.running, self.calls = running, []

        async def run(self, trigger, topic_ids):
            self.calls.append(topic_ids)

    fake = FakeCollector()
    app.state.tasks = set()
    app.dependency_overrides[trigger_permission] = lambda: Principal("svc", True, frozenset(authorities))
    app.dependency_overrides[collector_of] = lambda: fake
    return TestClient(app), fake


def test_service_scope_can_only_request_named_topics():
    from app.main import app

    try:
        client, fake = _client_as({"COLLECTION_REQUEST"})
        assert client.post("/api/v1/collections", json={}).status_code == 403
        assert client.post("/api/v1/collections", json={"topicIds": ["t1"]}).status_code == 202
        too_many = {"topicIds": [f"t{i}" for i in range(21)]}
        assert client.post("/api/v1/collections", json=too_many).status_code == 400
    finally:
        app.dependency_overrides.clear()


def test_admin_can_collect_everything_and_running_collection_conflicts():
    from app.main import app

    try:
        client, _ = _client_as({"PIPELINE_MANAGE"})
        assert client.post("/api/v1/collections", json={}).status_code == 202
        busy, _ = _client_as({"PIPELINE_MANAGE"}, running=True)
        assert busy.post("/api/v1/collections", json={}).status_code == 409
    finally:
        app.dependency_overrides.clear()


def test_zero_minimum_relevance_is_kept():
    from app.models import Topic

    base = {"id": "t", "slug": "s", "name": "n", "keywords": [], "maximumItemsPerRun": 10, "configuration": {}}
    assert Topic.from_api({**base, "minimumRelevance": 0}).min_relevance == 0
    assert Topic.from_api({**base, "minimumRelevance": None}).min_relevance == 0.55
