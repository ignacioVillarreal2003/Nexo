package com.nexo.content.domain.entity.enums;

/** Relación de un contenido con lo publicado anteriormente. */
public enum ContentRelation {
    /** Información sustancialmente diferente. */
    NEW,
    /** El mismo acontecimiento con un desarrollo relevante. */
    UPDATE,
    /** Esencialmente la misma información. */
    DUPLICATE
}
