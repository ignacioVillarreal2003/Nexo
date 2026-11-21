package com.nexo.topic.domain.dto.response;

import com.nexo.topic.domain.entity.enums.CatalogStatus;

import java.util.List;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String slug,
        String name,
        String description,
        String icon,
        String imageUrl,
        int sortOrder,
        CatalogStatus status,
        List<TopicResponse> topics) {

    public CategoryResponse withTopics(List<TopicResponse> topics) {
        return new CategoryResponse(id, slug, name, description, icon, imageUrl, sortOrder, status, topics);
    }
}
