package com.nexo.content.domain.entity;

import com.nexo.content.domain.entity.enums.AiMode;
import com.nexo.content.domain.entity.enums.BriefStatus;
import com.nexo.content.domain.entity.enums.ContentRelation;
import com.nexo.content.domain.entity.enums.ContentStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.nexo.content.domain.util.Texts.blankToNull;
import static com.nexo.content.domain.util.Texts.truncate;

/** Un artículo, noticia o publicación recopilado, procesado por la IA y deduplicado. */
@Entity
@Table(name = "contents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Content extends BaseEntity {

    public static final int EMBEDDING_DIMENSIONS = 384;

    @Column(name = "original_url", nullable = false, length = 2000)
    private String originalUrl;

    /** SHA-256 de la URL normalizada (deduplicación exacta). */
    @Column(name = "url_hash", nullable = false, unique = true, length = 64)
    private String urlHash;

    /** SHA-256 del texto normalizado (deduplicación exacta). */
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    /** SimHash de 64 bits del texto (deduplicación textual). */
    @Column(name = "simhash", nullable = false)
    private long simhash;

    /** Embedding de título + resumen (deduplicación semántica). */
    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = EMBEDDING_DIMENSIONS)
    @Column(name = "embedding")
    private float[] embedding;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "source_name", length = 200)
    private String sourceName;

    @Column(name = "original_title", nullable = false, length = 1000)
    private String originalTitle;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "summary", nullable = false, length = 5000)
    private String summary;

    @Column(name = "title_en", length = 500)
    private String titleEn;

    @Column(name = "summary_en", length = 5000)
    private String summaryEn;

    @Column(name = "body_excerpt", length = 2000)
    private String bodyExcerpt;

    /** Texto completo de la nota, tal como se pudo leer (base del informe). */
    @Column(name = "body_text", columnDefinition = "text")
    private String bodyText;

    // ---- Informe completo (lo escribe la IA cuando la noticia se elige para alguien).

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

    /** {@code null} si nadie lo pidió todavía. */
    @Enumerated(EnumType.STRING)
    @Column(name = "brief_status", length = 20)
    private BriefStatus briefStatus;

    @Column(name = "brief_requested_at")
    private Instant briefRequestedAt;

    @Column(name = "brief_attempts", nullable = false)
    private int briefAttempts;

    @Column(name = "brief_generated_at")
    private Instant briefGeneratedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "brief_mode", length = 30)
    private AiMode briefMode;

    @Column(name = "image_url", length = 2000)
    private String imageUrl;

    @Column(name = "author", length = 300)
    private String author;

    @Column(name = "language", length = 8)
    private String language;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "keywords", nullable = false, columnDefinition = "jsonb")
    private List<String> keywords;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "entities", nullable = false, columnDefinition = "jsonb")
    private List<String> entities;

    @Column(name = "publication_date")
    private OffsetDateTime publicationDate;

    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt;

    @Column(name = "quality_score")
    private Double qualityScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ContentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation", nullable = false, length = 20)
    private ContentRelation relation;

    @Column(name = "related_content_id")
    private UUID relatedContentId;

    @Column(name = "similarity")
    private Double similarity;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    /** Con qué se procesó al recopilarlo. */
    @Enumerated(EnumType.STRING)
    @Column(name = "ai_mode", length = 30)
    private AiMode aiMode;

    @Column(name = "edited_by_administrator", nullable = false)
    private boolean editedByAdministrator;

    @Column(name = "run_id")
    private UUID runId;

    @OneToMany(mappedBy = "content", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<ContentTopic> topics = new HashSet<>();

    public boolean isBriefPending() {
        return briefStatus == BriefStatus.PENDING;
    }

    public boolean hasBrief() {
        return briefStatus == BriefStatus.READY;
    }

    /** Encola el informe si todavía no lo tiene ni está pedido. @return {@code true} si quedó encolado ahora. */
    public boolean requestBrief() {
        if (briefStatus != null) {
            return false;
        }
        briefStatus = BriefStatus.PENDING;
        briefRequestedAt = Instant.now();
        briefAttempts = 0;
        return true;
    }

    /** Un intento fallido; tras {@code maximumAttempts} queda FAILED y no se vuelve a intentar. */
    public void briefAttemptFailed(int maximumAttempts) {
        briefAttempts++;
        if (briefAttempts >= maximumAttempts) {
            briefStatus = BriefStatus.FAILED;
        }
    }

    /**
     * Guarda el informe. Si lo escribió la IA a partir de la nota completa, también reemplaza el título y el resumen
     * corto (son mejores que los del análisis inicial, que a veces solo vio el copete), salvo que un administrador
     * los haya editado.
     */
    public void applyBrief(Brief brief) {
        this.detailedSummary = brief.detailedSummary();
        this.detailedSummaryEn = blankToNull(brief.detailedSummaryEn());
        this.keyPoints = brief.keyPoints() == null ? new ArrayList<>() : new ArrayList<>(brief.keyPoints());
        this.keyPointsEn = brief.keyPointsEn() == null ? new ArrayList<>() : new ArrayList<>(brief.keyPointsEn());
        this.whyItMatters = truncate(blankToNull(brief.whyItMatters()), 1500);
        this.whyItMattersEn = truncate(blankToNull(brief.whyItMattersEn()), 1500);
        this.readingMinutes = brief.readingMinutes();
        this.briefMode = brief.mode();
        this.briefStatus = BriefStatus.READY;
        this.briefGeneratedAt = Instant.now();
        if (imageUrl == null && brief.imageUrl() != null) {
            this.imageUrl = truncate(brief.imageUrl(), 2000);
        }
        // El enlace de Google News no sirve para leer: se pasa al del medio (el hash de la URL queda igual).
        if (brief.resolvedUrl() != null && originalUrl != null && originalUrl.contains("news.google.com")) {
            this.originalUrl = truncate(brief.resolvedUrl(), 2000);
        }
        boolean fromModel = brief.mode() == AiMode.GEMINI;
        if (fromModel && !editedByAdministrator && brief.titleEn() != null && !brief.titleEn().isBlank()) {
            this.title = truncate(brief.title(), 500);
            this.summary = truncate(brief.summary(), 5000);
            this.titleEn = truncate(brief.titleEn(), 500);
            this.summaryEn = truncate(brief.summaryEn(), 5000);
            this.language = "es";
        }
    }

    /** Lo que devuelve la IA al escribir el informe. */
    public record Brief(String title, String titleEn, String summary, String summaryEn, String detailedSummary,
                        String detailedSummaryEn, List<String> keyPoints, List<String> keyPointsEn,
                        String whyItMatters, String whyItMattersEn, Integer readingMinutes, AiMode mode,
                        String imageUrl, String resolvedUrl) {
    }

    public static Builder builder() {
        return new Builder();
    }

    public void linkTopic(UUID topicId, double relevance) {
        boolean present = topics.stream().anyMatch(t -> t.getTopicId().equals(topicId));
        if (!present) {
            topics.add(ContentTopic.of(this, topicId, relevance));
        }
    }

    public void review(String title, String summary, ContentStatus status) {
        if (title != null) {
            this.title = title;
        }
        if (summary != null) {
            this.summary = summary;
        }
        if (status != null) {
            this.status = status;
        }
        this.editedByAdministrator = true;
    }

    /** Construcción en pasos: lo que llega del collector, el análisis de la IA y la decisión de deduplicación. */
    public static final class Builder {

        private final Content content = new Content();

        private Builder() {
            content.collectedAt = Instant.now();
            content.keywords = new ArrayList<>();
            content.entities = new ArrayList<>();
            content.relation = ContentRelation.NEW;
        }

        public Builder origin(String originalUrl, String urlHash, String contentHash, long simhash, UUID runId) {
            content.originalUrl = originalUrl;
            content.urlHash = urlHash;
            content.contentHash = contentHash;
            content.simhash = simhash;
            content.runId = runId;
            return this;
        }

        public Builder source(UUID sourceId, String sourceName, String author, String imageUrl,
                              OffsetDateTime publicationDate) {
            content.sourceId = sourceId;
            content.sourceName = truncate(sourceName, 200);
            content.author = truncate(author, 300);
            content.imageUrl = truncate(imageUrl, 2000);
            content.publicationDate = publicationDate;
            return this;
        }

        /** Texto completo de la nota (hasta {@code max} caracteres). */
        public Builder body(String bodyText, int max) {
            content.bodyText = bodyText == null || bodyText.isBlank() ? null : truncate(bodyText, max);
            return this;
        }

        public Builder text(String originalTitle, String title, String summary, String bodyExcerpt, String language) {
            content.originalTitle = truncate(originalTitle, 1000);
            content.title = truncate(title, 500);
            content.summary = truncate(summary, 5000);
            content.bodyExcerpt = truncate(bodyExcerpt, 2000);
            content.language = truncate(language, 8);
            return this;
        }

        /** La misma noticia en inglés, si la IA la escribió. */
        public Builder english(String titleEn, String summaryEn) {
            content.titleEn = truncate(titleEn, 500);
            content.summaryEn = truncate(summaryEn, 5000);
            return this;
        }

        public Builder analysis(List<String> keywords, List<String> entities, Double qualityScore, AiMode aiMode,
                                float[] embedding) {
            content.keywords = keywords == null ? new ArrayList<>() : new ArrayList<>(keywords);
            content.entities = entities == null ? new ArrayList<>() : new ArrayList<>(entities);
            content.qualityScore = qualityScore;
            content.aiMode = aiMode;
            content.embedding = embedding;
            return this;
        }

        public Builder decision(ContentStatus status, ContentRelation relation, UUID relatedContentId,
                                Double similarity, String rejectionReason) {
            content.status = status;
            content.relation = relation;
            content.relatedContentId = relatedContentId;
            content.similarity = similarity == null ? null : Math.round(similarity * 1000.0) / 1000.0;
            content.rejectionReason = truncate(rejectionReason, 1000);
            return this;
        }

        public Content build() {
            return content;
        }
    }
}
