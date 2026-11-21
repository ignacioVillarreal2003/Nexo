package com.nexo.topic.domain.dto.request;

import com.nexo.topic.domain.entity.enums.SourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record SourceRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull SourceType type,
        @NotBlank @Size(max = 1000) String url,
        Map<String, Object> configuration) {
}
