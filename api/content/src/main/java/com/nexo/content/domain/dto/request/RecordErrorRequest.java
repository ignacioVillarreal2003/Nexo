package com.nexo.content.domain.dto.request;

import com.nexo.content.domain.entity.enums.PipelineStage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RecordErrorRequest(UUID runId, @NotNull PipelineStage stage, String source, UUID topicId,
                                 @NotBlank String message) {
}
