package com.nexo.subscription.domain.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateDeliverySettingsRequest(
        @NotBlank @Size(max = 64) String timezone,
        @Min(0) @Max(23) int deliveryHour,
        @Min(1) @Max(200) int maximumDailyItems,
        boolean paused) {
}
