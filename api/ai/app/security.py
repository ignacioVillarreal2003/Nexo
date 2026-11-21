"""Validación del JWT emitido por el servicio de autenticación, con su JWKS (igual que los servicios Java)."""

import logging
from dataclasses import dataclass
from functools import lru_cache

import jwt
from fastapi import Depends, Header, HTTPException

from .config import Settings, get_settings

log = logging.getLogger(__name__)


@dataclass(frozen=True)
class Principal:
    subject: str
    service: bool
    authorities: frozenset[str]


@lru_cache
def _jwks_client(url: str) -> jwt.PyJWKClient:
    return jwt.PyJWKClient(url, cache_keys=True, lifespan=600)


def decode(token: str, settings: Settings) -> Principal:
    try:
        signing_key = _jwks_client(settings.jwks_url).get_signing_key_from_jwt(token)
        claims = jwt.decode(token, signing_key.key, algorithms=["RS256"], issuer=settings.jwt_issuer,
                            options={"require": ["exp", "sub"]})
    except jwt.PyJWTError as exc:
        log.debug("Rejected token: %s", exc)
        raise HTTPException(status_code=401, detail="Invalid or expired token") from exc
    service = claims.get("type") == "service"
    values = claims.get("scopes" if service else "permissions") or []
    return Principal(subject=str(claims["sub"]), service=service, authorities=frozenset(map(str, values)))


def principal(authorization: str | None = Header(default=None),
              settings: Settings = Depends(get_settings)) -> Principal:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Missing bearer token")
    return decode(authorization[7:], settings)


def require_authority(authority: str):
    def check(user: Principal = Depends(principal)) -> Principal:
        if authority not in user.authorities:
            raise HTTPException(status_code=403, detail=f"Requires {authority}")
        return user
    return check
