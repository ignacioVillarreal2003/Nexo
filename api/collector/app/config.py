from functools import lru_cache

from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    # Orígenes del frontend autorizados (separados por coma), igual que en los servicios Java.
    cors_allowed_origins: str = "http://localhost:4300"

    kafka_bootstrap_servers: str = "localhost:29093"
    content_collected_topic: str = "nexo.content.collected"

    authentication_service_base_url: str = "http://localhost:8090"
    topic_service_base_url: str = "http://localhost:8092"
    subscription_service_base_url: str = "http://localhost:8093"
    content_service_base_url: str = "http://localhost:8094"

    # Cliente de servicio (client_credentials) con el que el collector llama a los demás servicios.
    authentication_service_client_id: str = "c0000000-0000-0000-0000-000000000001"
    authentication_service_client_secret: str = "nexo-collector-dev-secret"

    # JWT de los usuarios que disparan una recopilación manual (validado con el JWKS).
    jwks_url: str = "http://localhost:8090/.well-known/json-web-key-set.json"
    jwt_issuer: str = "nexo-authentication"
    manage_authority: str = "PIPELINE_MANAGE"
    read_authority: str = "PIPELINE_READ"
    # Scope de servicio para pedir una recopilación acotada a ciertos tópicos (lo usa delivery).
    request_authority: str = "COLLECTION_REQUEST"
    # Tope de tópicos por pedido a demanda.
    max_requested_topics: int = 20

    newsapi_key: str = ""

    # Cron (UTC) de la recopilación diaria; vacío la desactiva.
    collect_cron: str = "0 3 * * *"
    # La ejecución programada solo recopila tópicos con al menos un suscriptor.
    only_subscribed_topics: bool = True
    # Antigüedad máxima (horas) de lo que se recopila.
    max_age_hours: int = 72
    topic_concurrency: int = 4
    extraction_concurrency: int = 8
    http_timeout_seconds: float = 20.0
    # Por debajo de esta longitud se intenta extraer el texto completo del artículo.
    min_text_chars: int = 400
    user_agent: str = "NexoBot/1.0 (+https://nexo.local/bot)"


@lru_cache
def get_settings() -> Settings:
    return Settings()
