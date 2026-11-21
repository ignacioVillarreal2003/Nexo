package com.nexo.content.domain.entity.enums;

/** Estado del informe completo de un contenido ({@code null} mientras nadie lo pida). */
public enum BriefStatus {
    /** Pedido: la cola de informes lo va a escribir. */
    PENDING,
    /** Escrito y guardado. */
    READY,
    /** Se agotaron los intentos: no se vuelve a intentar solo. */
    FAILED
}
