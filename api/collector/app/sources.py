"""Conectores de fuentes. Cada tipo de fuente registra una función que devuelve documentos normalizados;
agregar un tipo nuevo no requiere tocar el pipeline."""

import calendar
import re
import urllib.parse
from collections.abc import Awaitable, Callable
from datetime import datetime, timedelta, timezone

import feedparser
import httpx

from .config import Settings
from .models import Document, Source, Topic
from .text import first_image, strip_html

Fetcher = Callable[[httpx.AsyncClient, Source, Topic, Settings], Awaitable[list[Document]]]
FETCHERS: dict[str, Fetcher] = {}


def fetcher(source_type: str):
    def register(fn: Fetcher) -> Fetcher:
        FETCHERS[source_type] = fn
        return fn
    return register


class SourceError(Exception):
    pass


# ---------------------------------------------------------------------------------------------- utilidades

def search_query(topic: Topic) -> str:
    if topic.search_query:
        return topic.search_query
    terms = [f'"{k}"' if " " in k else k for k in topic.keywords[:4]] or [topic.name]
    return " OR ".join(terms)


def _struct_to_dt(value) -> datetime | None:
    if not value:
        return None
    return datetime.fromtimestamp(calendar.timegm(value), tz=timezone.utc)


def _entry_image(entry) -> str | None:
    for key in ("media_content", "media_thumbnail"):
        for media in entry.get(key) or []:
            url = media.get("url")
            if url and (media.get("medium") in (None, "image") or key == "media_thumbnail"):
                return url
    for link in entry.get("links") or []:
        if link.get("rel") == "enclosure" and str(link.get("type", "")).startswith("image"):
            return link.get("href")
    return first_image(entry.get("summary"))


def _entry_text(entry) -> str:
    contents = entry.get("content") or []
    if contents:
        longest = max(contents, key=lambda c: len(c.get("value") or ""))
        text = strip_html(longest.get("value"))
        if text:
            return text
    return strip_html(entry.get("summary") or entry.get("description"))


async def _get(client: httpx.AsyncClient, url: str, **kwargs) -> httpx.Response:
    try:
        response = await client.get(url, follow_redirects=True, **kwargs)
    except httpx.HTTPError as exc:
        raise SourceError(f"Error de red: {exc.__class__.__name__}: {exc}") from exc
    if response.status_code >= 400:
        raise SourceError(f"HTTP {response.status_code} al consultar {url[:200]}")
    return response


def parse_feed(content: bytes, source: Source, aggregator: bool = False) -> list[Document]:
    feed = feedparser.parse(content)
    if feed.bozo and not feed.entries:
        raise SourceError(f"Feed inválido: {feed.get('bozo_exception')}")
    docs = []
    for entry in feed.entries[: source.max_items]:
        link, title = entry.get("link"), strip_html(entry.get("title"))
        if not link or not title:
            continue
        publisher = (entry.get("source") or {}).get("title") if aggregator else None
        text = _entry_text(entry)
        if aggregator and strip_html(text).startswith(title[:40]):
            text = ""  # Google News repite el titular en la descripción
        docs.append(Document(
            url=link, title=title, text=text,
            source_name=f"{publisher} · vía {source.name}" if publisher else source.name,
            image_url=_entry_image(entry), author=entry.get("author"),
            published_at=_struct_to_dt(entry.get("published_parsed") or entry.get("updated_parsed")),
            language=source.configuration.get("language"),
            extractable=not aggregator))
    return docs


# ---------------------------------------------------------------------------------------------- conectores

@fetcher("RSS")
async def fetch_rss(client, source, topic, settings):
    response = await _get(client, source.url)
    # Las ediciones por país de Google News son feeds fijos pero de agregador: solo traen el titular.
    return parse_feed(response.content, source, aggregator="news.google." in source.url)


@fetcher("RSS_SEARCH")
async def fetch_rss_search(client, source, topic, settings):
    url = source.url.replace("{query}", urllib.parse.quote_plus(search_query(topic)))
    response = await _get(client, url)
    aggregator = "news.google." in source.url
    return parse_feed(response.content, source, aggregator=aggregator)


@fetcher("ARXIV")
async def fetch_arxiv(client, source, topic, settings):
    if topic.arxiv_categories:
        query = " OR ".join(f"cat:{c}" for c in topic.arxiv_categories.split())
    else:
        query = " OR ".join(f'all:"{k}"' for k in topic.keywords[:3]) or f'all:"{topic.name}"'
    response = await _get(client, source.url, params={
        "search_query": query, "sortBy": "submittedDate", "sortOrder": "descending",
        "max_results": source.max_items})
    docs = parse_feed(response.content, source)
    for doc in docs:
        doc.title = re.sub(r"\s+", " ", doc.title)
        doc.language = "en"
        doc.extractable = False  # el abstract ya es el texto útil
    return docs


@fetcher("HACKERNEWS")
async def fetch_hackernews(client, source, topic, settings):
    since = int((datetime.now(timezone.utc) - timedelta(hours=settings.max_age_hours)).timestamp())
    min_points = int(source.configuration.get("minPoints", 20))
    # Algolia no admite OR: se consulta por las palabras clave (en inglés suelen dar mejores resultados).
    terms = [k for k in topic.keywords if k.isascii()][:3] or [topic.name]
    seen, docs = set(), []
    for term in terms:
        response = await _get(client, source.url, params={
            "query": term, "tags": "story", "hitsPerPage": source.max_items,
            "numericFilters": f"points>{min_points},created_at_i>{since}"})
        for hit in response.json().get("hits", []):
            url = hit.get("url") or f"https://news.ycombinator.com/item?id={hit.get('objectID')}"
            if url in seen or not hit.get("title"):
                continue
            seen.add(url)
            docs.append(Document(
                url=url, title=hit["title"], text=strip_html(hit.get("story_text")),
                source_name=f"{urllib.parse.urlparse(url).netloc.removeprefix('www.')} · vía Hacker News",
                author=hit.get("author"), language="en",
                published_at=datetime.fromtimestamp(hit.get("created_at_i", since), tz=timezone.utc)))
    return docs[: source.max_items]


@fetcher("NEWSAPI")
async def fetch_newsapi(client, source, topic, settings):
    if not settings.newsapi_key:
        raise SourceError("NEWSAPI_KEY no configurada")
    response = await _get(client, source.url, params={
        "q": search_query(topic), "language": source.configuration.get("language", "es"),
        "sortBy": "publishedAt", "pageSize": source.max_items, "apiKey": settings.newsapi_key})
    docs = []
    for a in response.json().get("articles", []):
        if not a.get("url") or not a.get("title") or a["title"] == "[Removed]":
            continue
        published = a.get("publishedAt")
        docs.append(Document(
            url=a["url"], title=a["title"],
            text=" ".join(filter(None, [a.get("description"), a.get("content")])),
            source_name=(a.get("source") or {}).get("name") or source.name,
            image_url=a.get("urlToImage"), author=a.get("author"),
            published_at=datetime.fromisoformat(published.replace("Z", "+00:00")) if published else None,
            language=source.configuration.get("language")))
    return docs
