package com.nexo.subscription.domain.dto.response;

import java.util.List;
import java.util.UUID;

/** Lo que el servicio de entrega necesita para armar el día de un usuario. */
public record DeliveryPlanResponse(
        UUID userId,
        String timezone,
        int deliveryHour,
        int maximumDailyItems,
        boolean paused,
        List<PlannedTopic> topics) {

    public record PlannedTopic(UUID topicId, int dailyLimit) {
    }
}
