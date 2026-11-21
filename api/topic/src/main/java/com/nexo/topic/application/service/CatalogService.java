package com.nexo.topic.application.service;

import com.nexo.topic.application.mapper.CatalogMapper;
import com.nexo.topic.domain.config.ApplicationProperties;
import com.nexo.topic.domain.dto.response.CategoryResponse;
import com.nexo.topic.domain.dto.response.CollectionPlanItemResponse;
import com.nexo.topic.domain.dto.response.TopicReferenceResponse;
import com.nexo.topic.domain.dto.response.TopicResponse;
import com.nexo.topic.domain.dto.response.TopicSourceResponse;
import com.nexo.topic.domain.entity.Topic;
import com.nexo.topic.domain.entity.enums.CatalogStatus;
import com.nexo.topic.domain.exception.ResourceNotFoundException;
import com.nexo.topic.domain.util.Slugs;
import com.nexo.topic.infrastructure.config.CacheNames;
import com.nexo.topic.infrastructure.repository.CategoryRepository;
import com.nexo.topic.infrastructure.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Lecturas del catálogo: públicas para la app y de servicio para el collector y la entrega. */
@Service
@RequiredArgsConstructor
public class CatalogService {

    private final CategoryRepository categoryRepository;
    private final TopicRepository topicRepository;
    private final CatalogMapper catalogMapper;
    private final ApplicationProperties properties;

    /** Categorías activas con sus tópicos activos, en orden de presentación. */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.CATALOG, key = "'tree'")
    public List<CategoryResponse> catalog() {
        Map<UUID, List<TopicResponse>> byCategory = topicRepository.findPublished(CatalogStatus.ACTIVE).stream()
                .collect(Collectors.groupingBy(t -> t.getCategory().getId(), LinkedHashMap::new,
                        Collectors.mapping(catalogMapper::toResponse, Collectors.toList())));
        return categoryRepository.findByStatusOrderBySortOrderAscNameAsc(CatalogStatus.ACTIVE).stream()
                .map(c -> catalogMapper.toResponse(c).withTopics(byCategory.getOrDefault(c.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TopicResponse> search(UUID categoryId, String query) {
        String needle = Slugs.fold(query);
        return topicRepository.findPublished(CatalogStatus.ACTIVE).stream()
                .filter(t -> categoryId == null || t.getCategory().getId().equals(categoryId))
                .filter(t -> needle.isEmpty() || matches(t, needle))
                .map(catalogMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TopicResponse get(String idOrSlug) {
        return catalogMapper.toResponse(find(idOrSlug));
    }

    /** Fuentes activas de las que se recopila el tópico, ordenadas por nombre. */
    @Transactional(readOnly = true)
    public List<TopicSourceResponse> sources(String idOrSlug) {
        return find(idOrSlug).getSources().stream()
                .filter(s -> s.getStatus() == CatalogStatus.ACTIVE)
                .map(s -> new TopicSourceResponse(s.getName(), s.getType()))
                .sorted(Comparator.comparing(TopicSourceResponse::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Plan de recopilación: tópicos publicados con al menos una fuente activa. */
    @Transactional(readOnly = true)
    public List<CollectionPlanItemResponse> collectionPlan(Collection<UUID> onlyTopicIds) {
        return topicRepository.findPublishedWithSources(CatalogStatus.ACTIVE).stream()
                .filter(t -> onlyTopicIds == null || onlyTopicIds.isEmpty() || onlyTopicIds.contains(t.getId()))
                .map(t -> new CollectionPlanItemResponse(catalogMapper.toPlanTopic(t), t.getSources().stream()
                        .filter(s -> s.isActive())
                        .map(catalogMapper::toResponse)
                        .toList()))
                .filter(item -> !item.sources().isEmpty())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TopicReferenceResponse> references(Collection<UUID> ids) {
        return topicRepository.findByIdIn(ids).stream().map(catalogMapper::toReference).toList();
    }

    private Topic find(String idOrSlug) {
        try {
            UUID id = UUID.fromString(idOrSlug);
            return topicRepository.findDetailedById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Topic", idOrSlug));
        } catch (IllegalArgumentException notAnId) {
            return topicRepository.findBySlug(idOrSlug)
                    .orElseThrow(() -> new ResourceNotFoundException("Topic", idOrSlug));
        }
    }

    private static boolean matches(Topic topic, String needle) {
        return Slugs.fold(topic.getName()).contains(needle)
                || Slugs.fold(topic.getDescription()).contains(needle)
                || topic.getKeywords().stream().anyMatch(k -> Slugs.fold(k).contains(needle));
    }
}
