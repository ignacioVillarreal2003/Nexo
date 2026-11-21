from dataclasses import dataclass
from datetime import datetime


@dataclass
class Topic:
    id: str
    slug: str
    name: str
    description: str | None
    keywords: list[str]
    search_query: str | None
    min_relevance: float
    max_items_per_run: int
    arxiv_categories: str | None

    @classmethod
    def from_api(cls, d: dict) -> "Topic":
        return cls(id=d["id"], slug=d["slug"], name=d["name"], description=d.get("description"),
                   keywords=d.get("keywords") or [], search_query=d.get("searchQuery"),
                   min_relevance=0.55 if d.get("minimumRelevance") is None else float(d["minimumRelevance"]),
                   max_items_per_run=int(d.get("maximumItemsPerRun") or 25),
                   arxiv_categories=d.get("arxivCategories"))


@dataclass
class Source:
    id: str
    name: str
    type: str
    url: str
    configuration: dict

    @classmethod
    def from_api(cls, d: dict) -> "Source":
        config = d.get("configuration") or {}
        return cls(id=d["id"], name=d["name"], type=d["type"], url=d["url"],
                   configuration=config if isinstance(config, dict) else {})

    @property
    def max_items(self) -> int:
        return int(self.configuration.get("maxItems", 15))


@dataclass
class Document:
    """Documento normalizado, independiente del tipo de fuente."""
    url: str
    title: str
    text: str = ""
    source_name: str = ""
    image_url: str | None = None
    author: str | None = None
    published_at: datetime | None = None
    language: str | None = None
    # Las URLs de agregadores (Google News) no permiten descargar el artículo.
    extractable: bool = True
