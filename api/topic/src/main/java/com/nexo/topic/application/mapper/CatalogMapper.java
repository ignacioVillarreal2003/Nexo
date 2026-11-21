package com.nexo.topic.application.mapper;

import com.nexo.topic.domain.dto.response.CategoryResponse;
import com.nexo.topic.domain.dto.response.CollectionPlanItemResponse;
import com.nexo.topic.domain.dto.response.SourceResponse;
import com.nexo.topic.domain.dto.response.TopicDetailResponse;
import com.nexo.topic.domain.dto.response.TopicReferenceResponse;
import com.nexo.topic.domain.dto.response.TopicResponse;
import com.nexo.topic.domain.entity.Category;
import com.nexo.topic.domain.entity.Source;
import com.nexo.topic.domain.entity.Topic;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Mapper
public interface CatalogMapper {

    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "categoryName", source = "category.name")
    TopicResponse toResponse(Topic topic);

    @Mapping(target = "topics", expression = "java(java.util.List.of())")
    CategoryResponse toResponse(Category category);

    SourceResponse toResponse(Source source);

    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "categoryName", source = "category.name")
    TopicReferenceResponse toReference(Topic topic);

    CollectionPlanItemResponse.PlanTopic toPlanTopic(Topic topic);

    default TopicDetailResponse toDetail(Topic topic) {
        List<UUID> sourceIds = topic.getSources().stream()
                .map(Source::getId)
                .sorted(Comparator.naturalOrder())
                .toList();
        return new TopicDetailResponse(toResponse(topic), topic.getSearchQuery(), topic.getMinimumRelevance(),
                topic.getMaximumItemsPerRun(), topic.getArxivCategories(), sourceIds);
    }
}
