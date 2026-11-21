package com.nexo.delivery.domain.entity.enums;

/** Estado de un correo de resumen encolado. */
public enum EmailRequestStatus {
    /** Esperando los informes de sus noticias. */
    PENDING,
    /** Enviado a notification. */
    SENT,
    /** No había nada para mandar. */
    EMPTY
}
