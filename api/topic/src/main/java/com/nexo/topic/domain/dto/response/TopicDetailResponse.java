package com.nexo.topic.domain.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Vista de administración: el tópico más su configuración de búsqueda y sus fuentes. */
public record TopicDetailResponse(
        TopicResponse topic,
        String searchQuery,
        BigDecimal minimumRelevance,
        int maximumItemsPerRun,
        String arxivCategories,
        List<UUID> sourceIds) {
}
