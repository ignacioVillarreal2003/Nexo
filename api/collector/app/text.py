"""Limpieza de HTML y extracción del texto principal de artículos."""

import html
import json
import logging
import re
import urllib.parse

import httpx

log = logging.getLogger(__name__)

_TAG = re.compile(r"<[^>]+>")
_IMG = re.compile(r"<img[^>]+src=[\"']([^\"']+)[\"']", re.I)
_WS = re.compile(r"\s+")


def strip_html(value: str | None) -> str:
    if not value:
        return ""
    text = _TAG.sub(" ", value)
    return _WS.sub(" ", html.unescape(text)).strip()


def first_image(value: str | None) -> str | None:
    if not value:
        return None
    m = _IMG.search(value)
    return m.group(1) if m and m.group(1).startswith("http") else None


async def extract_article(client: httpx.AsyncClient, url: str) -> tuple[str, str | None]:
    """Descarga la página y devuelve (texto principal, imagen). Vacío si no se pudo."""
    import trafilatura

    try:
        response = await client.get(url, follow_redirects=True)
        if response.status_code >= 400 or "html" not in response.headers.get("content-type", "html"):
            return "", None
        page = response.text
    except httpx.HTTPError as exc:
        log.debug("No se pudo descargar %s: %s", url, exc)
        return "", None
    text = trafilatura.extract(page, include_comments=False, include_tables=False, favor_precision=True) or ""
    image = None
    try:
        meta = trafilatura.extract_metadata(page)
        image = getattr(meta, "image", None) if meta else None
    except Exception:  # noqa: BLE001 - los metadatos son opcionales
        pass
    return text.strip(), image


_GN_SIGNATURE = re.compile(r'data-n-a-sg="([^"]+)"')
_GN_TIMESTAMP = re.compile(r'data-n-a-ts="([^"]+)"')
_GN_BATCH_URL = "https://news.google.com/_/DotsSplashUi/data/batchexecute"


def is_google_news(url: str) -> bool:
    parsed = urllib.parse.urlparse(url)
    return parsed.netloc == "news.google.com" and "/articles/" in parsed.path


async def resolve_google_news(client: httpx.AsyncClient, url: str) -> str | None:
    """Devuelve la URL real del artículo detrás de un enlace de Google News, o None si no se pudo.

    Los enlaces del RSS de Google News (news.google.com/rss/articles/<id>) no redirigen por HTTP:
    la página trae una firma y un timestamp con los que se pide la URL original al mismo endpoint
    que usa el sitio. Es un mecanismo no documentado, así que cualquier fallo se trata como "sin resolver"
    y el documento sigue su curso con el titular.
    """
    article_id = urllib.parse.urlparse(url).path.rstrip("/").split("/")[-1]
    try:
        page = (await client.get(f"https://news.google.com/rss/articles/{article_id}", follow_redirects=True)).text
        signature, timestamp = _GN_SIGNATURE.search(page), _GN_TIMESTAMP.search(page)
        if not signature or not timestamp:
            return None
        request = ["Fbv4je",
                   '["garturlreq",[["X","X",["X","X"],null,null,1,1,"US:en",null,1,null,null,null,null,null,0,1],'
                   f'"X","X",1,[1,1,1],1,1,null,0,0,null,0],"{article_id}",{timestamp.group(1)},"{signature.group(1)}"]']
        response = await client.post(_GN_BATCH_URL, data={"f.req": json.dumps([[request]])},
                                     headers={"Content-Type": "application/x-www-form-urlencoded;charset=UTF-8"})
        body = response.text.split("\n\n", 1)[1]
        resolved = json.loads(json.loads(body)[0][2])[1]
    except (httpx.HTTPError, IndexError, KeyError, TypeError, ValueError) as exc:
        log.debug("No se pudo resolver %s: %s", url[:120], exc)
        return None
    return resolved if isinstance(resolved, str) and resolved.startswith("http") else None
