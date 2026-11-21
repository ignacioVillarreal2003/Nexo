package com.nexo.content.domain.dto.response;

import com.nexo.content.domain.entity.enums.ContentRelation;
import com.nexo.content.domain.entity.enums.ContentStatus;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ContentResponse(
        UUID id,
        String originalUrl,
        String sourceName,
        String originalTitle,
        String title,
        String summary,
        String bodyExcerpt,
        String imageUrl,
        String author,
        String language,
        List<String> keywords,
        List<String> entities,
        OffsetDateTime publicationDate,
        Instant collectedAt,
        Double qualityScore,
        ContentStatus status,
        ContentRelation relation,
        UUID relatedContentId,
        Double similarity,
        String rejectionReason,
        String aiMode,
        boolean editedByAdministrator,
        List<UUID> topicIds) {
}
