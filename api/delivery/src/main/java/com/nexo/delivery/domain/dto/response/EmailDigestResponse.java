package com.nexo.delivery.domain.dto.response;

/**
 * @param emailed    contenidos incluidos en el correo (0 si el día está vacío).
 * @param collecting {@code true} si algunos tópicos no tenían contenido y se pidió recopilarlos ahora.
 */
public record EmailDigestResponse(int emailed, boolean collecting) {
}
