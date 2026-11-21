package com.nexo.content.domain.dto.request;

import com.nexo.content.domain.entity.enums.RunTrigger;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record StartRunRequest(@NotNull RunTrigger trigger, @Min(0) int topicsPlanned) {
}
