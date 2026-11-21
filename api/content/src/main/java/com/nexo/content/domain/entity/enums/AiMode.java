package com.nexo.content.domain.entity.enums;

/** Con qué se procesó un contenido o se escribió su informe. */
public enum AiMode {
    /** El modelo de lenguaje. */
    GEMINI,
    /** Heurísticas (no hay clave de la IA configurada). */
    HEURISTIC,
    /** Heurísticas porque el modelo no estaba disponible. */
    HEURISTIC_FALLBACK;

    /** Lo que informa el servicio de IA; un valor desconocido se toma como heurístico. */
    public static AiMode from(String value) {
        if (value == null) {
            return HEURISTIC;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException unknown) {
            return HEURISTIC;
        }
    }
}
