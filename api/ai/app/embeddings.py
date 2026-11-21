"""Embeddings locales con sentence-transformers (sin coste por llamada)."""

import logging
import threading
from typing import Protocol

import numpy as np

log = logging.getLogger(__name__)


class Embedder(Protocol):
    dimension: int

    def embed(self, texts: list[str]) -> list[list[float]]: ...


class SentenceTransformerEmbedder:
    """Carga el modelo de forma perezosa y thread-safe; los vectores salen normalizados (norma 1)."""

    def __init__(self, model_name: str, dimension: int):
        self.model_name = model_name
        self.dimension = dimension
        self._model = None
        self._lock = threading.Lock()

    def _load(self):
        if self._model is None:
            with self._lock:
                if self._model is None:
                    from sentence_transformers import SentenceTransformer

                    log.info("Cargando modelo de embeddings %s", self.model_name)
                    model = SentenceTransformer(self.model_name, device="cpu")
                    dim = model.get_sentence_embedding_dimension()
                    if dim != self.dimension:
                        raise RuntimeError(
                            f"El modelo produce vectores de {dim} dimensiones; la base de datos espera {self.dimension}")
                    self._model = model
        return self._model

    def warm_up(self) -> None:
        self.embed(["warm up"])

    def embed(self, texts: list[str]) -> list[list[float]]:
        vectors = self._load().encode(texts, normalize_embeddings=True, batch_size=32, show_progress_bar=False)
        return np.asarray(vectors, dtype=np.float32).round(6).tolist()


def cosine(a: list[float], b: list[float]) -> float:
    va, vb = np.asarray(a), np.asarray(b)
    denom = float(np.linalg.norm(va) * np.linalg.norm(vb))
    return float(va @ vb / denom) if denom else 0.0
