package com.nexo.delivery.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Una fila por usuario, tópico y día ya entregados: hace idempotente la entrega diaria. */
@Entity
@Table(name = "delivery_batches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryBatch {

    @EmbeddedId
    private Id id;

    @Column(name = "items", nullable = false)
    private int items;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static DeliveryBatch of(UUID userId, UUID topicId, LocalDate deliveryDate, int items) {
        DeliveryBatch batch = new DeliveryBatch();
        batch.id = new Id(userId, topicId, deliveryDate);
        batch.items = items;
        batch.createdAt = Instant.now();
        return batch;
    }

    @Embeddable
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class Id implements Serializable {

        @Column(name = "user_id")
        private UUID userId;

        @Column(name = "topic_id")
        private UUID topicId;

        @Column(name = "delivery_date")
        private LocalDate deliveryDate;

        public Id(UUID userId, UUID topicId, LocalDate deliveryDate) {
            this.userId = userId;
            this.topicId = topicId;
            this.deliveryDate = deliveryDate;
        }
    }
}
