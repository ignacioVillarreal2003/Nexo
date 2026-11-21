package com.nexo.topic.domain.entity.enums;

/** Forma de consultar una fuente. El collector implementa un conector por tipo. */
public enum SourceType {
    /** Feed RSS o Atom fijo. */
    RSS,
    /** Plantilla de URL RSS con {@code {query}}, construida a partir de la consulta del tópico. */
    RSS_SEARCH,
    /** API de arXiv (publicaciones científicas). */
    ARXIV,
    /** Búsqueda de Hacker News (Algolia). */
    HACKERNEWS,
    /** newsapi.org; requiere clave en el collector. */
    NEWSAPI
}
