package com.nexo.topic.domain.dto.response;

import com.nexo.topic.domain.entity.enums.CatalogStatus;
import com.nexo.topic.domain.entity.enums.SourceType;

import java.util.Map;
import java.util.UUID;

public record SourceResponse(
        UUID id,
        String name,
        SourceType type,
        String url,
        Map<String, Object> configuration,
        CatalogStatus status) {
}
