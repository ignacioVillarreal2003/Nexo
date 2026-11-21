package com.nexo.content.application.mapper;

import com.nexo.content.domain.dto.response.ContentResponse;
import com.nexo.content.domain.entity.Content;
import com.nexo.content.domain.entity.ContentTopic;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ContentMapper {

    @Mapping(target = "topicIds", source = "topics")
    ContentResponse toResponse(Content content);

    default List<UUID> topicIds(java.util.Set<ContentTopic> topics) {
        return topics.stream().map(ContentTopic::getTopicId).sorted().toList();
    }
}
