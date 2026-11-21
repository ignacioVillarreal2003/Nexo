"""Envoltorio de respuesta compartido por todas las APIs de la plataforma (ApiResponse)."""

from datetime import datetime, timezone


def ok(data, message: str | None = None, request_id: str | None = None) -> dict:
    return {
        "success": True,
        "data": data,
        "message": message,
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "requestId": request_id,
    }
