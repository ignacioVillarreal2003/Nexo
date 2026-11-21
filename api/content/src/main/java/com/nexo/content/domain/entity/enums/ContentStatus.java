package com.nexo.content.domain.entity.enums;

public enum ContentStatus {
    /** Disponible para las entregas. */
    PUBLISHED,
    /** Repite información ya publicada; se guarda para auditoría y deduplicación exacta. */
    DUPLICATE,
    /** No relevante para el tópico o de calidad insuficiente. */
    REJECTED,
    /** Retirado manualmente desde la administración. */
    HIDDEN
}
