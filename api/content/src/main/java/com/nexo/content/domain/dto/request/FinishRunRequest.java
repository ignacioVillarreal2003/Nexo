package com.nexo.content.domain.dto.request;

import com.nexo.content.domain.entity.enums.RunStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FinishRunRequest(@NotNull RunStatus status, @Min(0) int itemsCollected, @Size(max = 2000) String notes) {
}
