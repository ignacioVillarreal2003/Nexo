package com.nexo.subscription.domain.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SubscribeRequest(@NotNull UUID topicId, @Min(1) @Max(50) Integer dailyLimit) {
}
