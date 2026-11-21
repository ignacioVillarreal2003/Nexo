package com.nexo.topic.domain.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Lo que el collector necesita para recopilar un tópico: su contexto y sus fuentes activas. */
public record CollectionPlanItemResponse(PlanTopic topic, List<SourceResponse> sources) {

    public record PlanTopic(
            UUID id,
            String slug,
            String name,
            String description,
            List<String> keywords,
            String searchQuery,
            BigDecimal minimumRelevance,
            int maximumItemsPerRun,
            String arxivCategories) {
    }
}
