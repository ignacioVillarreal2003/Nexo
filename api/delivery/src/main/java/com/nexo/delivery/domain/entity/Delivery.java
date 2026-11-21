package com.nexo.delivery.domain.entity;

import com.nexo.delivery.domain.entity.enums.DeliveryRelation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * UserContentDelivery: un contenido entregado a un usuario. Guarda una copia del contenido para que
 * "Mi día" y el historial no dependan de otro servicio, y su embedding para la antirrepetición por usuario.
 */
@Entity
@Table(name = "deliveries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Delivery extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "content_id", nullable = false, updatable = false)
    private UUID contentId;

    @Column(name = "topic_id", nullable = false, updatable = false)
    private UUID topicId;

    @Column(name = "topic_name", length = 120)
    private String topicName;

    /** Fecha local del usuario a la que pertenece la entrega. */
    @Column(name = "delivery_date", nullable = false, updatable = false)
    private LocalDate deliveryDate;

    @Column(name = "delivered_at", nullable = false, updatable = false)
    private Instant deliveredAt;

    @Column(name = "read_at")
    private Instant readAt;

    /** Cuándo salió en un resumen por correo; {@code null} si todavía no se mandó. */
    @Column(name = "emailed_at")
    private Instant emailedAt;

    @Column(name = "favorite", nullable = false)
    private boolean favorite;

    @Column(name = "favorited_at")
    private Instant favoritedAt;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "summary", nullable = false, length = 5000)
    private String summary;

    /** La misma noticia en inglés; {@code null} si no hay traducción (se muestra la principal). */
    @Column(name = "title_en", length = 500)
    private String titleEn;

    @Column(name = "summary_en", length = 5000)
    private String summaryEn;

    // ---- Informe completo (copia del que escribe content al elegir la noticia).

    @Column(name = "detailed_summary", columnDefinition = "text")
    private String detailedSummary;

    @Column(name = "detailed_summary_en", columnDefinition = "text")
    private String detailedSummaryEn;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "key_points", nullable = false, columnDefinition = "jsonb")
    private List<String> keyPoints = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "key_points_en", nullable = false, columnDefinition = "jsonb")
    private List<String> keyPointsEn = new ArrayList<>();

    @Column(name = "why_it_matters", length = 1500)
    private String whyItMatters;

    @Column(name = "why_it_matters_en", length = 1500)
    private String whyItMattersEn;

    @Column(name = "reading_minutes")
    private Integer readingMinutes;

    @Column(name = "brief_ready", nullable = false)
    private boolean briefReady;

    @Column(name = "original_url", nullable = false, length = 2000)
    private String originalUrl;

    @Column(name = "source_name", length = 200)
    private String sourceName;

    @Column(name = "image_url", length = 2000)
    private String imageUrl;

    @Column(name = "publication_date")
    private Instant publicationDate;

    @Column(name = "collected_at")
    private Instant collectedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation", nullable = false, length = 20)
    private DeliveryRelation relation;

    @Column(name = "related_content_id")
    private UUID relatedContentId;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 384)
    @Column(name = "embedding")
    private float[] embedding;

    public static Delivery of(UUID userId, UUID topicId, String topicName, LocalDate deliveryDate,
                              UUID contentId, String title, String summary, String originalUrl, String sourceName,
                              String imageUrl, Instant publicationDate, Instant collectedAt,
                              DeliveryRelation relation, UUID relatedContentId, float[] embedding) {
        Delivery delivery = new Delivery();
        delivery.userId = userId;
        delivery.topicId = topicId;
        delivery.topicName = topicName;
        delivery.deliveryDate = deliveryDate;
        delivery.deliveredAt = Instant.now();
        delivery.contentId = contentId;
        delivery.title = title;
        delivery.summary = summary;
        delivery.originalUrl = originalUrl;
        delivery.sourceName = sourceName;
        delivery.imageUrl = imageUrl;
        delivery.publicationDate = publicationDate;
        delivery.collectedAt = collectedAt;
        delivery.relation = relation;
        delivery.relatedContentId = relatedContentId;
        delivery.embedding = embedding;
        return delivery;
    }

    public Delivery withEnglish(String titleEn, String summaryEn) {
        this.titleEn = titleEn;
        this.summaryEn = summaryEn;
        return this;
    }

    /**
     * Copia el informe completo y, con él, el título y el resumen corto (que el informe pudo mejorar al leer la nota
     * entera), la imagen y el enlace al medio si faltaban.
     */
    public void applyBrief(String title, String summary, String titleEn, String summaryEn, String detailedSummary,
                           String detailedSummaryEn, List<String> keyPoints, List<String> keyPointsEn,
                           String whyItMatters, String whyItMattersEn, Integer readingMinutes, String imageUrl,
                           String originalUrl) {
        if (title != null && !title.isBlank()) {
            this.title = title;
        }
        if (summary != null && !summary.isBlank()) {
            this.summary = summary;
        }
        if (titleEn != null && !titleEn.isBlank()) {
            this.titleEn = titleEn;
            this.summaryEn = summaryEn;
        }
        this.detailedSummary = detailedSummary;
        this.detailedSummaryEn = detailedSummaryEn;
        this.keyPoints = keyPoints == null ? new ArrayList<>() : new ArrayList<>(keyPoints);
        this.keyPointsEn = keyPointsEn == null ? new ArrayList<>() : new ArrayList<>(keyPointsEn);
        this.whyItMatters = whyItMatters;
        this.whyItMattersEn = whyItMattersEn;
        this.readingMinutes = readingMinutes;
        if (this.imageUrl == null && imageUrl != null) {
            this.imageUrl = imageUrl;
        }
        if (originalUrl != null && !originalUrl.isBlank()) {
            this.originalUrl = originalUrl;
        }
        this.briefReady = true;
    }

    public void markRead(boolean read) {
        if (read && readAt == null) {
            readAt = Instant.now();
        } else if (!read) {
            readAt = null;
        }
    }

    public void markFavorite(boolean favorite) {
        this.favorite = favorite;
        this.favoritedAt = favorite ? Instant.now() : null;
    }
}
