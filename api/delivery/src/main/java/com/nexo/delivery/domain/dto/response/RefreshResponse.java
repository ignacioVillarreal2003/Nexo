package com.nexo.delivery.domain.dto.response;

/**
 * @param delivered  contenidos entregados ahora.
 * @param collecting {@code true} si algunos tópicos no tenían contenido y se pidió recopilarlos: conviene
 *                   volver a intentar en unos minutos.
 */
public record RefreshResponse(int delivered, boolean collecting) {
}
