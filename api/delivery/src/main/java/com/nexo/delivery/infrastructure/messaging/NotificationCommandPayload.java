package com.nexo.delivery.infrastructure.messaging;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Comando para el servicio de notificaciones ({@code nexo.notifications.commands}). */
public record NotificationCommandPayload(
        Integer schemaVersion,
        UUID messageId,
        Instant occurredAt,
        String category,
        UUID actorId,
        Attributes attributes,
        Recipient recipient,
        InAppMessage inApp,
        EmailMessage email) {

    public static final String CATEGORY_DAILY_DIGEST = "DELIVERY_DAILY_DIGEST";
    /** "Enviarme por mail": categoría obligatoria, porque es un pedido explícito del lector. */
    public static final String CATEGORY_ON_DEMAND = "DELIVERY_ON_DEMAND";
    public static final String TEMPLATE_DAILY_DIGEST = "delivery.daily-digest";

    public String partitionKey() {
        return recipient.userId() != null ? recipient.userId().toString() : recipient.email();
    }

    public record Recipient(UUID userId, String email, String displayName) {
    }

    public record InAppMessage(String type, String title, String body, Map<String, String> data) {
    }

    public record EmailMessage(String template, Map<String, String> variables, String subject, String htmlBody) {
    }

    public record Attributes(String severity, Short priority, String groupKey,
                             String targetType, UUID targetId, Instant expiresAt) {
    }
}
