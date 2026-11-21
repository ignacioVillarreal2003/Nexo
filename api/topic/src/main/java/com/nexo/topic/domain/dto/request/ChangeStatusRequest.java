package com.nexo.topic.domain.dto.request;

import com.nexo.topic.domain.entity.enums.CatalogStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeStatusRequest(@NotNull CatalogStatus status) {
}
