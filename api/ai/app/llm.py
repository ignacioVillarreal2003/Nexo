"""Análisis y comparación de contenidos con Gemini usando salida estructurada (JSON con esquema)."""

import asyncio
import logging
import time

from google import genai
from google.genai import errors, types

from .config import Settings
from .schemas import ComparedItem, LlmAnalysis, LlmBrief, LlmComparison, TopicContext

log = logging.getLogger(__name__)

LANGUAGE_NAMES = {"es": "español", "en": "inglés", "pt": "portugués"}

ANALYSIS_SYSTEM = """Eres el editor de una plataforma que entrega cada día contenido nuevo sobre temas que el lector sigue.
Evalúas un artículo recopilado automáticamente para un tópico concreto.

Reglas:
- Basa todo exclusivamente en el texto recibido. No agregues datos, cifras, fechas ni opiniones que no estén en él.
- Si solo tienes el titular o un fragmento breve, el resumen debe ser una sola oración que reformule lo disponible,
  sin completar con suposiciones, y la calidad no debe superar 0.5.
- "relevant" es true solo si el tópico es un tema central del artículo, no una mención de pasada.
- Penaliza en calidad: clickbait, publicidad, contenido promocional, listados vacíos, páginas sin sustancia.
- Escribe título, resumen y palabras clave en {language}, aunque el original esté en otro idioma.
- El título: descriptivo y neutral, máximo 110 caracteres. El resumen: 2 a 4 oraciones, máximo 600 caracteres.
- Además, en title_en y summary_en escribe la traducción al inglés de ese mismo título y resumen (fiel, sin agregar nada)."""

BRIEF_SYSTEM = """Sos el editor de Nexo, un servicio que le manda a cada lector las noticias del día sobre los temas que sigue.
Recibís el texto completo de una nota ya elegida para el lector y escribís su informe, para que pueda entender la
noticia completa sin abrir la nota original.

Reglas:
- Todo sale exclusivamente del texto recibido. No agregues datos, cifras, fechas, nombres ni opiniones que no estén en él.
- Si el texto es corto o es solo el titular, el informe es proporcional: no inventes desarrollo para alargarlo.
- Tono periodístico, neutral y claro. Sin clickbait, sin frases como "en este artículo" o "la nota dice".
- title: descriptivo, máximo 110 caracteres.
- summary: 2 a 3 oraciones (máximo 450 caracteres) con lo esencial.
- detailed_summary: el resumen completo, de 3 a 6 párrafos (unas 200 a 450 palabras según la extensión de la nota),
  separados por una línea en blanco. Cubrí qué pasó, quiénes participan, cifras y datos relevantes, contexto,
  reacciones o posiciones y qué se espera a continuación, en la medida en que el texto lo cuente.
- key_points: 3 a 6 puntos clave, una oración concreta cada uno (datos, no generalidades).
- why_it_matters: por qué es relevante para alguien que sigue el tema, en 1 a 2 oraciones; vacío si el texto no da base.
- Escribí todo en español rioplatense neutro y, en los campos *_en, la traducción fiel al inglés de lo mismo."""

COMPARISON_SYSTEM = """Comparas dos contenidos sobre temas parecidos para decidir si el lector, que ya recibió el ANTERIOR,
debe recibir también el NUEVO.

- DUPLICATE: el NUEVO transmite esencialmente la misma información (otra redacción, otro medio, mismos hechos).
- UPDATE: es el mismo acontecimiento pero el NUEVO aporta un desarrollo relevante (nuevos datos, desenlace, reacción oficial, cifras actualizadas).
- NEW: comparten tema general pero tratan hechos o información sustancialmente diferentes.

Responde con una justificación breve en español."""


# Errores transitorios del lado de Google: se prueba con el siguiente modelo de la cadena.
TRANSIENT_CODES = (408, 500, 502, 503, 504)
RETIRED_MODEL_PAUSE_SECONDS = 24 * 60 * 60


class LlmUnavailable(RuntimeError):
    """Gemini está en enfriamiento (cuota agotada o todos los modelos caídos)."""


class RateLimiter:
    """Espaciado uniforme de pedidos: como máximo `per_minute` por minuto."""

    def __init__(self, per_minute: int):
        self.interval = 60.0 / max(per_minute, 1)
        self.next_slot = 0.0
        self.lock = asyncio.Lock()

    async def acquire(self) -> None:
        async with self.lock:
            now = time.monotonic()
            wait = self.next_slot - now
            self.next_slot = max(now, self.next_slot) + self.interval
        if wait > 0:
            await asyncio.sleep(wait)


class GeminiClient:
    """Cliente de Gemini que nunca bloquea el pipeline.

    - Cada pedido tiene un tiempo máximo (`llm_timeout_seconds`).
    - Se recorre la cadena de modelos: ante un error transitorio o un timeout se prueba el siguiente.
    - La cuota es por modelo: un 429 pausa solo ese modelo durante `llm_cooldown_seconds`.
    - Si no queda ningún modelo disponible se abre un enfriamiento general corto; mientras dura,
      el servicio responde con heurísticas sin llamar a Google.
    """

    def __init__(self, settings: Settings):
        self.settings = settings
        self.client = genai.Client(api_key=settings.gemini_api_key)
        self.semaphore = asyncio.Semaphore(settings.llm_concurrency)
        self.limiter = RateLimiter(settings.llm_requests_per_minute)
        self.cooldown_until = 0.0
        self.models = settings.model_chain
        self.model_cooldowns: dict[str, float] = {}

    @property
    def cooling_down(self) -> bool:
        return time.monotonic() < self.cooldown_until

    def available_models(self) -> list[str]:
        now = time.monotonic()
        return [m for m in self.models if self.model_cooldowns.get(m, 0.0) <= now]

    def _cool_down(self, seconds: int, reason: str) -> None:
        self.cooldown_until = time.monotonic() + seconds
        log.warning("%s; se usan heurísticas durante %ss", reason, seconds)

    async def _call(self, system: str, prompt: str, schema: type, timeout: float | None = None,
                    temperature: float = 0.2):
        if self.cooling_down:
            raise LlmUnavailable("Gemini en enfriamiento")
        last_error: BaseException | None = None
        for model in self.available_models():
            try:
                return await self._generate(model, system, prompt, schema, timeout, temperature)
            except errors.APIError as exc:
                if exc.code == 429:
                    self.model_cooldowns[model] = time.monotonic() + self.settings.llm_cooldown_seconds
                    log.warning("Cuota agotada en %s; se pausa %ss y se prueba el siguiente modelo",
                                model, self.settings.llm_cooldown_seconds)
                elif exc.code == 404:
                    # Modelo retirado o no habilitado para esta clave: se lo saca de la cadena por un día.
                    self.model_cooldowns[model] = time.monotonic() + RETIRED_MODEL_PAUSE_SECONDS
                    log.warning("El modelo %s no está disponible para esta clave; se omite por 24 h", model)
                elif exc.code in TRANSIENT_CODES:
                    log.info("Gemini %s respondió %s; se prueba el siguiente modelo", model, exc.code)
                else:
                    raise
                last_error = exc
            except (asyncio.TimeoutError, ConnectionError) as exc:
                log.info("Gemini %s no respondió a tiempo; se prueba el siguiente modelo", model)
                last_error = exc
        self._cool_down(self.settings.llm_unavailable_cooldown_seconds, "Ningún modelo de Gemini disponible")
        raise LlmUnavailable("Ningún modelo de Gemini disponible") from last_error

    @staticmethod
    def _thinking(model: str) -> types.ThinkingConfig | None:
        # Resumir y clasificar no necesita razonamiento largo: con "low" la respuesta tarda segundos.
        # Solo la familia 3 acepta thinking_level; en 2.5 se deja el valor por defecto.
        return types.ThinkingConfig(thinking_level="low") if model.startswith("gemini-3") else None

    async def _generate(self, model: str, system: str, prompt: str, schema: type, timeout: float | None = None,
                        temperature: float = 0.2):
        await self.limiter.acquire()
        async with self.semaphore:
            response = await asyncio.wait_for(
                self.client.aio.models.generate_content(
                    model=model,
                    contents=prompt,
                    config=types.GenerateContentConfig(
                        system_instruction=system,
                        response_mime_type="application/json",
                        response_schema=schema,
                        temperature=temperature,
                        thinking_config=self._thinking(model),
                    ),
                ),
                timeout=timeout or self.settings.llm_timeout_seconds,
            )
        parsed = response.parsed
        if parsed is None:
            parsed = schema.model_validate_json(response.text or "{}")
        return parsed

    async def analyze(self, topic: TopicContext, title: str, text: str, language: str) -> LlmAnalysis:
        excerpt = text[: self.settings.max_input_chars] if text else "(sin texto: solo se dispone del titular)"
        prompt = (
            f"TÓPICO: {topic.name}\n"
            f"Descripción del tópico: {topic.description or '-'}\n"
            f"Palabras clave del tópico: {', '.join(topic.keywords) or '-'}\n\n"
            f"TITULAR ORIGINAL: {title}\n\n"
            f"TEXTO:\n{excerpt}"
        )
        system = ANALYSIS_SYSTEM.format(language=LANGUAGE_NAMES.get(language, language))
        return await self._call(system, prompt, LlmAnalysis)

    async def brief(self, title: str, text: str, source: str | None, topic: str | None,
                    published: str | None) -> LlmBrief:
        excerpt = text[: self.settings.brief_max_input_chars] if text else "(sin texto: solo se dispone del titular)"
        prompt = (
            f"TEMA QUE SIGUE EL LECTOR: {topic or '-'}\n"
            f"MEDIO: {source or '-'}\n"
            f"PUBLICADA: {published or 'fecha desconocida'}\n\n"
            f"TITULAR ORIGINAL: {title}\n\n"
            f"TEXTO DE LA NOTA:\n{excerpt}"
        )
        return await self._call(BRIEF_SYSTEM, prompt, LlmBrief, timeout=self.settings.brief_timeout_seconds,
                                temperature=0.3)

    async def compare(self, previous: ComparedItem, candidate: ComparedItem) -> LlmComparison:
        prompt = (
            f"ANTERIOR (publicado {previous.published_at or 'fecha desconocida'}):\n"
            f"{previous.title}\n{previous.summary}\n\n"
            f"NUEVO (publicado {candidate.published_at or 'fecha desconocida'}):\n"
            f"{candidate.title}\n{candidate.summary}"
        )
        return await self._call(COMPARISON_SYSTEM, prompt, LlmComparison)
