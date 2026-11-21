package com.nexo.subscription.domain.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateSubscriptionRequest(@Min(1) @Max(50) int dailyLimit) {
}
