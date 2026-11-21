package com.nexo.subscription.domain.dto.response;

public record DeliverySettingsResponse(String timezone, int deliveryHour, int maximumDailyItems, boolean paused) {
}
