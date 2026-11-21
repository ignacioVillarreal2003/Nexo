package com.nexo.content.infrastructure.messaging;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Documento recopilado por el collector y publicado en {@code nexo.content.collected}. */
public record ContentCollectedEvent(
        UUID runId,
        UUID topicId,
        String topicName,
        String topicDescription,
        List<String> topicKeywords,
        Double minimumRelevance,
        UUID sourceId,
        String sourceName,
        String url,
        String title,
        String text,
        String imageUrl,
        String author,
        OffsetDateTime publishedAt,
        String language) {

    public boolean isComplete() {
        return url != null && !url.isBlank() && title != null && !title.isBlank() && topicId != null;
    }
}
