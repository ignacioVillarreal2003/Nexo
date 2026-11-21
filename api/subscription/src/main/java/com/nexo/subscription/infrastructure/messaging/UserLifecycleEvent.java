package com.nexo.subscription.infrastructure.messaging;

import java.time.Instant;
import java.util.UUID;

/** Evento del servicio de autenticación ({@code nexo.authentication.user-events}). */
public record UserLifecycleEvent(
        UserEventType eventType,
        UUID userId,
        String email,
        String firstName,
        String lastName,
        String locale,
        Instant occurredAt) {
}
