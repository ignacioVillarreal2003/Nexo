package com.nexo.content.domain.util;

/** Operaciones de texto que se repetían en la ingestión, la entidad y la revisión. */
public final class Texts {

    private Texts() {
    }

    /** Recorta a {@code max} caracteres (las columnas tienen largo fijo); {@code null} queda {@code null}. */
    public static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** {@code value} si tiene texto; si no, {@code fallback}. */
    public static String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
