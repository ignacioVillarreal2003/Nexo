package com.nexo.delivery.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Fila del outbox transaccional. Se escribe en la misma transacción que la entrega y se publica
 * en Kafka de forma asíncrona: entrega al menos una vez y sin escrituras dobles inconsistentes.
 */
@Entity
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    private static final int MAX_ERROR_LENGTH = 2000;

    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 255)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "topic", nullable = false, length = 255)
    private String topic;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    public static OutboxEvent forTopic(String topic, String messageType, String partitionKey, UUID messageId,
                                       String payloadJson, String correlationId) {
        OutboxEvent outbox = new OutboxEvent();
        outbox.id = messageId;
        outbox.aggregateType = messageType;
        outbox.aggregateId = partitionKey;
        outbox.eventType = messageType;
        outbox.topic = topic;
        outbox.correlationId = correlationId;
        outbox.payload = payloadJson;
        outbox.createdAt = Instant.now();
        return outbox;
    }

    public void markPublished() {
        this.publishedAt = Instant.now();
        this.lastError = null;
    }

    public void discard(String reason) {
        this.publishedAt = Instant.now();
        this.lastError = truncate(reason);
    }

    public void markFailed(String error) {
        this.attempts++;
        this.lastError = truncate(error);
    }

    private static String truncate(String error) {
        return error != null && error.length() > MAX_ERROR_LENGTH ? error.substring(0, MAX_ERROR_LENGTH) : error;
    }
}
