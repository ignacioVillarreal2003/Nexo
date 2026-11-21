package com.nexo.delivery.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.delivery.domain.config.ApplicationProperties;
import com.nexo.delivery.domain.entity.OutboxEvent;
import com.nexo.delivery.infrastructure.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Encola comandos para el servicio de notificaciones a través del outbox transaccional: una entrega que
 * se revierte nunca notifica, y una confirmada se notifica al menos una vez.
 */
@Component
@RequiredArgsConstructor
public class NotificationCommandPublisher {

    private static final String MESSAGE_TYPE = "notification-command";

    private final OutboxEventRepository outboxEventRepository;
    private final ApplicationProperties properties;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(NotificationCommandPayload payload) {
        outboxEventRepository.save(OutboxEvent.forTopic(
                properties.events().notificationCommandsTopic(),
                MESSAGE_TYPE,
                payload.partitionKey(),
                payload.messageId(),
                serialize(payload),
                MessageCorrelation.current()));
    }

    private String serialize(NotificationCommandPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize notification command", e);
        }
    }
}
