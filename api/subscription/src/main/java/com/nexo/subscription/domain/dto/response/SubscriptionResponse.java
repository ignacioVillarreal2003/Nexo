package com.nexo.subscription.domain.dto.response;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionResponse(
        UUID id,
        UUID topicId,
        String topicName,
        String topicSlug,
        String topicIcon,
        String categoryName,
        int dailyLimit,
        Instant subscribedAt) {
}
