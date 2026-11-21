package com.nexo.delivery.domain.dto.response;

import com.nexo.delivery.domain.entity.enums.DeliveryRelation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record FeedItemResponse(
        UUID id,
        UUID contentId,
        UUID topicId,
        String topicName,
        LocalDate deliveryDate,
        Instant deliveredAt,
        Instant readAt,
        boolean favorite,
        String title,
        String summary,
        String titleEn,
        String summaryEn,
        String detailedSummary,
        String detailedSummaryEn,
        List<String> keyPoints,
        List<String> keyPointsEn,
        String whyItMatters,
        String whyItMattersEn,
        Integer readingMinutes,
        String originalUrl,
        String sourceName,
        String imageUrl,
        Instant publicationDate,
        Instant collectedAt,
        DeliveryRelation relation) {
}
