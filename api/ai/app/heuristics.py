"""Procesamiento sin LLM. Se usa cuando no hay GEMINI_API_KEY o cuando Gemini falla tras los reintentos.

Es deliberadamente conservador: el resumen es extractivo (frases del propio texto) para no introducir
información que la fuente no contiene.
"""

import re
import unicodedata
from collections import Counter
from datetime import datetime

STOPWORDS = {
    "es": set("""a al algo algunas algunos ante antes como con contra cual cuando de del desde donde durante e el ella
        ellas ellos en entre era es esa ese eso esta este esto estos fue han hasta hay la las le les lo los mas más me
        mi muy no nos o otra otro para pero por que qué se sea sin sobre su sus también tiene todo tras un una uno unos
        y ya son ser será sido está están según cada año años hoy nuevo nueva puede""".split()),
    "en": set("""a about after all also an and any are as at be been but by can could did do does for from had has
        have he her his how i if in into is it its just more most new no not of on one or our out over said she so
        some than that the their them there they this to up was we were what when which who will with would you
        your""".split()),
    "pt": set("""a ao aos as com da das de do dos e em entre era foi há isso mais mas na nas no nos o os ou para pela
        pelo por que se sem seu sua são também um uma""".split()),
}
ALL_STOPWORDS = set().union(*STOPWORDS.values())

_SENTENCE_SPLIT = re.compile(r"(?<=[.!?¡¿])\s+(?=[A-ZÁÉÍÓÚÑ0-9\"“«])")
_WORD = re.compile(r"[A-Za-zÁÉÍÓÚÜÑáéíóúüñ0-9][A-Za-zÁÉÍÓÚÜÑáéíóúüñ0-9+\-]*")
_ENTITY = re.compile(r"\b([A-ZÁÉÍÓÚÑ][\wáéíóúñ]+(?:\s+(?:de\s+|del\s+|of\s+)?[A-ZÁÉÍÓÚÑ][\wáéíóúñ]+)*)")


def fold(text: str) -> str:
    return "".join(c for c in unicodedata.normalize("NFD", text.lower()) if unicodedata.category(c) != "Mn")


def detect_language(text: str) -> str:
    words = [w.lower() for w in _WORD.findall(text[:3000])]
    if not words:
        return "es"
    scores = {lang: sum(1 for w in words if w in sw) for lang, sw in STOPWORDS.items()}
    return max(scores, key=scores.get)


def clean_title(title: str) -> str:
    """Quita el sufijo " - Medio" que agregan agregadores como Google News."""
    title = re.sub(r"\s+", " ", title).strip()
    parts = re.split(r"\s+[-|–]\s+", title)
    if len(parts) > 1 and len(parts[-1]) <= 40:
        title = " - ".join(parts[:-1])
    return title[:200]


def sentences(text: str) -> list[str]:
    text = re.sub(r"\s+", " ", text).strip()
    return [s.strip() for s in _SENTENCE_SPLIT.split(text) if len(s.strip()) > 25]


def extractive_summary(title: str, text: str, max_chars: int = 480) -> str:
    picked: list[str] = []
    total = 0
    for s in sentences(text):
        if fold(s) == fold(title):
            continue
        if total + len(s) > max_chars and picked:
            break
        picked.append(s if s.endswith((".", "!", "?")) else s + ".")
        total += len(s)
        if len(picked) >= 3:
            break
    if not picked:
        return clean_title(title).rstrip(".") + "."
    return " ".join(picked)[: max_chars + 40]


def extractive_brief(title: str, text: str) -> tuple[str, list[str]]:
    """Informe sin LLM: los primeros párrafos de la nota (hasta ~2200 caracteres, de a 3 oraciones) y, como puntos
    clave, las oraciones más informativas (las que tienen cifras o nombres propios)."""
    picked = [s if s.endswith((".", "!", "?")) else s + "." for s in sentences(text) if fold(s) != fold(title)]
    if not picked:
        return clean_title(title).rstrip(".") + ".", []
    body, total = [], 0
    for s in picked:
        if total + len(s) > 2200 and body:
            break
        body.append(s)
        total += len(s)
    paragraphs = [" ".join(body[i:i + 3]) for i in range(0, len(body), 3)]
    scored = sorted(picked[:25], key=lambda s: (len(re.findall(r"\d", s)) > 0) + len(_ENTITY.findall(s)) * 0.3,
                    reverse=True)
    points = [s for s in scored if 40 <= len(s) <= 260][:4]
    return "\n\n".join(paragraphs), points


def keywords(title: str, text: str, limit: int = 6) -> list[str]:
    words = [w for w in _WORD.findall(f"{title} {title} {text[:4000]}") if len(w) > 3 and fold(w) not in ALL_STOPWORDS]
    counts = Counter(fold(w) for w in words)
    display: dict[str, str] = {}
    for w in words:
        display.setdefault(fold(w), w if w.isupper() else w.lower())
    return [display[k] for k, _ in counts.most_common(limit)]


def entities(text: str, limit: int = 6) -> list[str]:
    found: Counter[str] = Counter()
    for s in sentences(text) or [text]:
        for match in _ENTITY.finditer(s):
            candidate = match.group(1)
            if s.startswith(candidate) and " " not in candidate:
                continue  # primera palabra de la oración: mayúscula gramatical, no nombre propio
            if fold(candidate) in ALL_STOPWORDS or len(candidate) < 3:
                continue
            found[candidate] += 1
    return [e for e, _ in found.most_common(limit)]


def keyword_hits(topic_keywords: list[str], title: str, text: str) -> int:
    haystack = fold(f"{title} {text[:5000]}")
    return sum(1 for k in topic_keywords if k and fold(k) in haystack)


def relevance_score(similarity: float, hits: int) -> float:
    """Combina similitud semántica tópico↔contenido con coincidencias de palabras clave."""
    base = (similarity - 0.12) / 0.45
    return round(max(0.0, min(1.0, base + 0.12 * min(hits, 3))), 3)


def quality_score(title: str, text: str) -> float:
    length = len(text)
    score = 0.4 if length < 200 else 0.55 if length < 800 else 0.7 if length < 3000 else 0.8
    if re.search(r"[!?]{2,}", title) or (title.isupper() and len(title) > 15):
        score -= 0.2
    if re.search(r"(?i)\b(no vas a creer|you won't believe|increíble truco|shocking)\b", title):
        score -= 0.2
    return round(max(0.05, min(1.0, score)), 3)


def compare(prev_title: str, prev_date: datetime | None, cand_title: str, cand_date: datetime | None,
            similarity: float) -> tuple[str, str]:
    """Distingue duplicado, actualización o nuevo sin LLM (la similitud de los embeddings ya incluye el resumen)."""
    a, b = set(fold(prev_title).split()), set(fold(cand_title).split())
    jaccard = len(a & b) / max(1, len(a | b))
    hours = abs((cand_date - prev_date).total_seconds()) / 3600 if prev_date and cand_date else 0
    if similarity >= 0.88 and (jaccard >= 0.5 or hours < 12):
        return "DUPLICATE", f"Muy similar (sim={similarity:.2f}, títulos={jaccard:.2f})"
    if hours >= 12 and jaccard < 0.6:
        return "UPDATE", f"Mismo tema {hours:.0f} h después con titular distinto"
    if similarity >= 0.86:
        return "DUPLICATE", f"Contenido casi equivalente (sim={similarity:.2f})"
    return "NEW", f"Información distinta (sim={similarity:.2f})"
