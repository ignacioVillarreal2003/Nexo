package com.nexo.topic.domain.dto.response;

import com.nexo.topic.domain.entity.enums.CatalogStatus;
import com.nexo.topic.domain.entity.enums.TopicKind;

import java.util.List;
import java.util.UUID;

public record TopicResponse(
        UUID id,
        UUID categoryId,
        String categoryName,
        String slug,
        String name,
        String description,
        String icon,
        String imageUrl,
        List<String> keywords,
        CatalogStatus status,
        TopicKind kind,
        String countryCode) {
}
