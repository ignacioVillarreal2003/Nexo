package com.nexo.delivery.domain.dto.request;

import java.util.UUID;

/** Sin {@code userId}, la entrega corre para todos los usuarios con suscripciones. */
public record RunDeliveryRequest(UUID userId) {
}
