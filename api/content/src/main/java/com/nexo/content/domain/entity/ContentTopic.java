package com.nexo.content.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** Un contenido puede ser relevante para varios tópicos, cada uno con su relevancia. */
@Entity
@Table(name = "content_topics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentTopic {

    @EmbeddedId
    private Id id;

    @MapsId("contentId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id")
    private Content content;

    @Column(name = "relevance", nullable = false)
    private double relevance;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    static ContentTopic of(Content content, UUID topicId, double relevance) {
        ContentTopic link = new ContentTopic();
        link.id = new Id(null, topicId);
        link.content = content;
        link.relevance = Math.round(relevance * 1000.0) / 1000.0;
        link.createdAt = Instant.now();
        return link;
    }

    public UUID getTopicId() {
        return id.topicId;
    }

    @Embeddable
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class Id implements Serializable {

        @Column(name = "content_id")
        private UUID contentId;

        @Column(name = "topic_id")
        private UUID topicId;

        Id(UUID contentId, UUID topicId) {
            this.contentId = contentId;
            this.topicId = topicId;
        }
    }
}
