package com.nexo.subscription.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/** Cuándo y cuánto contenido quiere recibir un usuario cada día. */
@Entity
@Table(name = "delivery_settings")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliverySettings {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone;

    /** Hora local (0–23) de la entrega diaria. */
    @Column(name = "delivery_hour", nullable = false)
    private int deliveryHour;

    /** Tope global de contenidos por día, sumando todos los tópicos. */
    @Column(name = "maximum_daily_items", nullable = false)
    private int maximumDailyItems;

    @Column(name = "paused", nullable = false)
    private boolean paused;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public static DeliverySettings create(UUID userId, String timezone, int deliveryHour, int maximumDailyItems) {
        DeliverySettings settings = new DeliverySettings();
        settings.userId = userId;
        settings.timezone = timezone;
        settings.deliveryHour = deliveryHour;
        settings.maximumDailyItems = maximumDailyItems;
        return settings;
    }

    public void update(String timezone, int deliveryHour, int maximumDailyItems, boolean paused) {
        this.timezone = timezone;
        this.deliveryHour = deliveryHour;
        this.maximumDailyItems = maximumDailyItems;
        this.paused = paused;
    }
}
