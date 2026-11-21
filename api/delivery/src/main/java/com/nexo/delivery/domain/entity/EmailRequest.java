package com.nexo.delivery.domain.entity;

import com.nexo.delivery.domain.entity.enums.EmailRequestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Un correo de resumen por mandar. No sale en el momento: espera a que estén los informes completos de sus noticias
 * (o un tiempo máximo) y lo manda {@code DigestDispatchService}.
 */
@Entity
@Table(name = "email_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailRequest extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "delivery_date", nullable = false, updatable = false)
    private LocalDate deliveryDate;

    /** Pedido con "Enviarme por mail" (todo el día) o resumen automático (solo lo que no salió por mail). */
    @Column(name = "on_demand", nullable = false, updatable = false)
    private boolean onDemand;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EmailRequestStatus status;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "item_count", nullable = false)
    private int itemCount;

    public static EmailRequest of(UUID userId, LocalDate deliveryDate, boolean onDemand) {
        EmailRequest request = new EmailRequest();
        request.userId = userId;
        request.deliveryDate = deliveryDate;
        request.onDemand = onDemand;
        request.status = EmailRequestStatus.PENDING;
        request.requestedAt = Instant.now();
        return request;
    }

    public boolean isPending() {
        return status == EmailRequestStatus.PENDING;
    }

    public void sent(int items) {
        status = EmailRequestStatus.SENT;
        itemCount = items;
        sentAt = Instant.now();
    }

    public void empty() {
        status = EmailRequestStatus.EMPTY;
        sentAt = Instant.now();
    }
}
