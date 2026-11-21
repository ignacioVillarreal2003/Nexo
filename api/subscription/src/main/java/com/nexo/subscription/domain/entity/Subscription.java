package com.nexo.subscription.domain.entity;

import com.nexo.subscription.domain.entity.enums.SubscriptionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Suscripción de un usuario a un tópico. Al cancelarla se conserva la fila para poder reactivarla. */
@Entity
@Table(name = "subscriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Subscription extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "topic_id", nullable = false, updatable = false)
    private UUID topicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SubscriptionStatus status;

    /** Contenidos de este tópico que el usuario quiere recibir por día. */
    @Column(name = "daily_content_limit", nullable = false)
    private int dailyContentLimit;

    @Column(name = "subscribed_at", nullable = false)
    private Instant subscribedAt;

    public static Subscription create(UUID userId, UUID topicId, int dailyContentLimit) {
        Subscription subscription = new Subscription();
        subscription.userId = userId;
        subscription.topicId = topicId;
        subscription.status = SubscriptionStatus.ACTIVE;
        subscription.dailyContentLimit = dailyContentLimit;
        subscription.subscribedAt = Instant.now();
        return subscription;
    }

    public void activate() {
        if (status != SubscriptionStatus.ACTIVE) {
            status = SubscriptionStatus.ACTIVE;
            subscribedAt = Instant.now();
        }
    }

    public void cancel() {
        status = SubscriptionStatus.CANCELLED;
    }

    public void changeDailyContentLimit(int dailyContentLimit) {
        this.dailyContentLimit = dailyContentLimit;
    }

    public boolean isActive() {
        return status == SubscriptionStatus.ACTIVE;
    }
}
