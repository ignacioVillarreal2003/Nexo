package com.nexo.topic.application.service;

import com.nexo.topic.application.mapper.CatalogMapper;
import com.nexo.topic.application.security.AuthenticatedUser;
import com.nexo.topic.domain.dto.request.CategoryRequest;
import com.nexo.topic.domain.dto.request.SourceRequest;
import com.nexo.topic.domain.dto.request.TopicRequest;
import com.nexo.topic.domain.dto.response.CategoryResponse;
import com.nexo.topic.domain.dto.response.SourceResponse;
import com.nexo.topic.domain.dto.response.TopicDetailResponse;
import com.nexo.topic.domain.dto.response.TopicResponse;
import com.nexo.topic.domain.entity.AuditLog;
import com.nexo.topic.domain.entity.Category;
import com.nexo.topic.domain.entity.Source;
import com.nexo.topic.domain.entity.Topic;
import com.nexo.topic.domain.entity.enums.CatalogStatus;
import com.nexo.topic.domain.entity.enums.SourceType;
import com.nexo.topic.domain.exception.BadRequestException;
import com.nexo.topic.domain.exception.ConflictException;
import com.nexo.topic.domain.exception.ResourceNotFoundException;
import com.nexo.topic.domain.util.Slugs;
import com.nexo.topic.infrastructure.config.CacheNames;
import com.nexo.topic.infrastructure.repository.CategoryRepository;
import com.nexo.topic.infrastructure.repository.SourceRepository;
import com.nexo.topic.infrastructure.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Alta, edición y desactivación de categorías, tópicos y fuentes desde el panel de administración. */
@Service
@RequiredArgsConstructor
public class CatalogAdministrationService {

    public static final String CATEGORY_SAVED = "CATEGORY_SAVED";
    public static final String CATEGORY_STATUS_CHANGED = "CATEGORY_STATUS_CHANGED";
    public static final String TOPIC_SAVED = "TOPIC_SAVED";
    public static final String TOPIC_STATUS_CHANGED = "TOPIC_STATUS_CHANGED";
    public static final String SOURCE_SAVED = "SOURCE_SAVED";
    public static final String SOURCE_STATUS_CHANGED = "SOURCE_STATUS_CHANGED";

    private final CategoryRepository categoryRepository;
    private final TopicRepository topicRepository;
    private final SourceRepository sourceRepository;
    private final CatalogMapper catalogMapper;
    private final AuditService auditService;

    // ---------------------------------------------------------------- categorías

    @Transactional(readOnly = true)
    public List<CategoryResponse> categories() {
        Map<UUID, List<TopicResponse>> byCategory = topicRepository.findAllForAdministration().stream()
                .collect(Collectors.groupingBy(t -> t.getCategory().getId(),
                        Collectors.mapping(catalogMapper::toResponse, Collectors.toList())));
        return categoryRepository.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(c -> catalogMapper.toResponse(c).withTopics(byCategory.getOrDefault(c.getId(), List.of())))
                .toList();
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.CATALOG, allEntries = true)
    public CategoryResponse saveCategory(UUID id, CategoryRequest request, AuthenticatedUser actor) {
        String slug = slugOf(request.slug(), request.name());
        Category category = id == null ? Category.create(slug, request.name().trim())
                : categoryRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category", id));
        categoryRepository.findBySlug(slug)
                .filter(other -> !other.getId().equals(category.getId()))
                .ifPresent(other -> {
                    throw new ConflictException("A category with slug '" + slug + "' already exists");
                });
        category.update(slug, request.name().trim(), request.description(), blankToNull(request.icon()),
                blankToNull(request.imageUrl()), request.sortOrder());
        Category saved = categoryRepository.save(category);
        audit(actor, CATEGORY_SAVED, "Category", saved.getId(), "slug", slug);
        return catalogMapper.toResponse(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.CATALOG, allEntries = true)
    public CategoryResponse changeCategoryStatus(UUID id, CatalogStatus status, AuthenticatedUser actor) {
        Category category = categoryRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category", id));
        category.changeStatus(status);
        audit(actor, CATEGORY_STATUS_CHANGED, "Category", id, "status", status.name());
        return catalogMapper.toResponse(category);
    }

    // ---------------------------------------------------------------- tópicos

    @Transactional(readOnly = true)
    public List<TopicDetailResponse> topics() {
        return topicRepository.findAllForAdministration().stream().map(catalogMapper::toDetail).toList();
    }

    @Transactional(readOnly = true)
    public TopicDetailResponse topic(UUID id) {
        return catalogMapper.toDetail(topicRepository.findDetailedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Topic", id)));
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.CATALOG, allEntries = true)
    public TopicDetailResponse saveTopic(UUID id, TopicRequest request, AuthenticatedUser actor) {
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", request.categoryId()));
        String slug = slugOf(request.slug(), request.name());
        Topic topic = id == null ? Topic.create(category, slug, request.name().trim())
                : topicRepository.findDetailedById(id).orElseThrow(() -> new ResourceNotFoundException("Topic", id));
        topicRepository.findBySlug(slug)
                .filter(other -> !other.getId().equals(topic.getId()))
                .ifPresent(other -> {
                    throw new ConflictException("A topic with slug '" + slug + "' already exists");
                });
        List<String> keywords = request.keywords() == null ? List.of()
                : request.keywords().stream().map(String::trim).distinct().toList();
        topic.update(category, slug, request.name().trim(), request.description(), blankToNull(request.icon()),
                blankToNull(request.imageUrl()), keywords);
        topic.configureSearch(blankToNull(request.searchQuery()),
                request.minimumRelevance() != null ? request.minimumRelevance() : topic.getMinimumRelevance(),
                request.maximumItemsPerRun() != null ? request.maximumItemsPerRun() : topic.getMaximumItemsPerRun(),
                blankToNull(request.arxivCategories()));
        topic.classify(request.kind() != null ? request.kind() : topic.getKind(), request.countryCode());
        if (request.sourceIds() != null) {
            List<Source> sources = sourceRepository.findAllById(request.sourceIds());
            if (sources.size() != request.sourceIds().stream().distinct().count()) {
                throw new BadRequestException("One or more sources do not exist");
            }
            topic.replaceSources(sources);
        }
        Topic saved = topicRepository.save(topic);
        audit(actor, TOPIC_SAVED, "Topic", saved.getId(), "slug", slug);
        return catalogMapper.toDetail(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.CATALOG, allEntries = true)
    public TopicDetailResponse changeTopicStatus(UUID id, CatalogStatus status, AuthenticatedUser actor) {
        Topic topic = topicRepository.findDetailedById(id).orElseThrow(() -> new ResourceNotFoundException("Topic", id));
        topic.changeStatus(status);
        audit(actor, TOPIC_STATUS_CHANGED, "Topic", id, "status", status.name());
        return catalogMapper.toDetail(topic);
    }

    // ---------------------------------------------------------------- fuentes

    @Transactional(readOnly = true)
    public List<SourceResponse> sources() {
        return sourceRepository.findAllByOrderByNameAsc().stream().map(catalogMapper::toResponse).toList();
    }

    @Transactional
    public SourceResponse saveSource(UUID id, SourceRequest request, AuthenticatedUser actor) {
        if (request.type() == SourceType.RSS_SEARCH && !request.url().contains("{query}")) {
            throw new BadRequestException("RSS_SEARCH sources must include {query} in their URL");
        }
        if (!request.url().startsWith("https://") && !request.url().startsWith("http://")) {
            throw new BadRequestException("The source URL must use http or https");
        }
        Source source;
        if (id == null) {
            source = Source.create(request.name().trim(), request.type(), request.url().trim(), request.configuration());
        } else {
            source = sourceRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Source", id));
            source.update(request.name().trim(), request.type(), request.url().trim(), request.configuration());
        }
        Source saved = sourceRepository.save(source);
        audit(actor, SOURCE_SAVED, "Source", saved.getId(), "type", request.type().name());
        return catalogMapper.toResponse(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.CATALOG, allEntries = true)
    public SourceResponse changeSourceStatus(UUID id, CatalogStatus status, AuthenticatedUser actor) {
        Source source = sourceRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Source", id));
        source.changeStatus(status);
        audit(actor, SOURCE_STATUS_CHANGED, "Source", id, "status", status.name());
        return catalogMapper.toResponse(source);
    }

    // ---------------------------------------------------------------- utilidades

    private void audit(AuthenticatedUser actor, String action, String resourceType, UUID resourceId,
                       Object... details) {
        auditService.record(actor.id(), actor.email(), action, resourceType, resourceId.toString(),
                AuditLog.Result.SUCCESS, AuditService.details(details));
    }

    private static String slugOf(String slug, String name) {
        String value = Slugs.slugify(slug == null || slug.isBlank() ? name : slug);
        if (value.isEmpty()) {
            throw new BadRequestException("The slug cannot be empty");
        }
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
