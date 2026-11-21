"""Lectura de la nota original: descarga la página y extrae el texto principal (y la imagen).

Se usa al preparar el informe completo de una noticia elegida: lo que guardó la recopilación puede ser solo el
copete del feed, así que se entra a la nota para tener el texto entero. Los enlaces de Google News se resuelven
primero a la URL real del medio (mismo mecanismo que usa el collector).
"""

import json
import logging
import re
import urllib.parse
from dataclasses import dataclass

import httpx

log = logging.getLogger(__name__)

USER_AGENT = "Mozilla/5.0 (compatible; NexoReader/1.0; +https://nexo.local)"

_GN_SIGNATURE = re.compile(r'data-n-a-sg="([^"]+)"')
_GN_TIMESTAMP = re.compile(r'data-n-a-ts="([^"]+)"')
_GN_BATCH_URL = "https://news.google.com/_/DotsSplashUi/data/batchexecute"


@dataclass
class Article:
    url: str
    text: str = ""
    image_url: str | None = None


def is_google_news(url: str) -> bool:
    parsed = urllib.parse.urlparse(url)
    return parsed.netloc == "news.google.com" and "/articles/" in parsed.path


def _safe(url: str | None) -> bool:
    return bool(url) and url.startswith(("http://", "https://"))


async def _resolve_google_news(client: httpx.AsyncClient, url: str) -> str | None:
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
    return resolved if isinstance(resolved, str) and _safe(resolved) else None


def _extract(page: str) -> tuple[str, str | None]:
    import trafilatura

    text = trafilatura.extract(page, include_comments=False, include_tables=False, favor_precision=True) or ""
    image = None
    try:
        meta = trafilatura.extract_metadata(page)
        image = getattr(meta, "image", None) if meta else None
    except Exception:  # noqa: BLE001 - los metadatos son opcionales
        pass
    return text.strip(), image if _safe(image) else None


async def read_article(url: str, timeout_seconds: float = 15) -> Article:
    """Texto principal de la nota. Si no se puede leer (paywall, bloqueo, error), devuelve el artículo vacío."""
    if not _safe(url):
        return Article(url=url)
    headers = {"User-Agent": USER_AGENT, "Accept-Language": "es,en;q=0.8"}
    try:
        async with httpx.AsyncClient(timeout=timeout_seconds, headers=headers, follow_redirects=True) as client:
            if is_google_news(url):
                url = await _resolve_google_news(client, url) or url
                if is_google_news(url):
                    return Article(url=url)
            response = await client.get(url)
            if response.status_code >= 400 or "html" not in response.headers.get("content-type", "html"):
                return Article(url=str(response.url))
            text, image = _extract(response.text)
            return Article(url=str(response.url), text=text, image_url=image)
    except httpx.HTTPError as exc:
        log.info("No se pudo leer la nota %s: %s", url[:120], exc)
        return Article(url=url)
