package com.nexo.content.domain.entity.enums;

public enum RunStatus {
    RUNNING,
    COMPLETED,
    PARTIAL,
    FAILED;

    public boolean isFinal() {
        return this != RUNNING;
    }
}
