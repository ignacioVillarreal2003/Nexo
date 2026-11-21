from functools import lru_cache

from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    gemini_api_key: str = ""
    gemini_model: str = "gemini-3.5-flash"
    # Modelos alternativos (separados por coma) si el principal está saturado, sin cuota o no responde.
    # En el plan gratuito la cuota es por modelo: cuantos más modelos, más capacidad total.
    gemini_fallback_models: str = "gemini-3.6-flash,gemini-3.5-flash-lite,gemini-3.1-flash-lite"
    embedding_model: str = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
    embedding_dim: int = 384
    summary_language: str = "es"
    # Máximo de caracteres del texto original que se envían al LLM.
    max_input_chars: int = 8000
    # Llamadas simultáneas a Gemini (protege la cuota).
    llm_concurrency: int = 4
    # Límite de pedidos por minuto a Gemini (el plan gratuito permite ~10-15).
    llm_requests_per_minute: int = 10
    # Tras un 429 que agota los reintentos, se usan heurísticas durante este tiempo.
    llm_cooldown_seconds: int = 120
    # Si fallan todos los modelos (503 / timeouts), pausa más corta antes de volver a probar.
    llm_unavailable_cooldown_seconds: int = 60
    # Tiempo máximo de cada pedido a Gemini.
    llm_timeout_seconds: float = 20
    # El informe completo es una respuesta larga: se le da más tiempo y más texto de entrada.
    brief_timeout_seconds: float = 60
    brief_max_input_chars: int = 16000
    # Con menos texto que esto se entra a la nota original para leerla entera.
    brief_min_text_chars: int = 2500

    # JWT del servicio de autenticación (validado con su JWKS).
    jwks_url: str = "http://localhost:8090/.well-known/json-web-key-set.json"
    jwt_issuer: str = "nexo-authentication"
    # Scope que debe tener el token de servicio que llama al análisis.
    required_authority: str = "AI_ANALYZE"

    @property
    def model_chain(self) -> list[str]:
        chain = [self.gemini_model] + [m.strip() for m in self.gemini_fallback_models.split(",")]
        return list(dict.fromkeys(m for m in chain if m))

    @property
    def llm_enabled(self) -> bool:
        return bool(self.gemini_api_key)


@lru_cache
def get_settings() -> Settings:
    return Settings()
