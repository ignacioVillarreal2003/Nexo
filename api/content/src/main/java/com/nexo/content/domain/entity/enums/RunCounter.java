package com.nexo.content.domain.entity.enums;

/** Contadores de una ejecución que se incrementan a medida que se procesan los documentos. */
public enum RunCounter {
    PROCESSED("items_processed"),
    PUBLISHED("items_published"),
    DUPLICATE("items_duplicate"),
    REJECTED("items_rejected"),
    FAILED("items_failed");

    private final String column;

    RunCounter(String column) {
        this.column = column;
    }

    public String column() {
        return column;
    }
}
