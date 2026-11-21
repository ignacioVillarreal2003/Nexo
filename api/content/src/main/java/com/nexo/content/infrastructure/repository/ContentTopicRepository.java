package com.nexo.content.infrastructure.repository;

import org.springframework.data.repository.Repository;
import com.nexo.content.domain.entity.ContentTopic;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface ContentTopicRepository extends Repository<ContentTopic, ContentTopic.Id> {

    /** Vincula un contenido existente a otro tópico; idempotente. */
    @Modifying
    @Query(value = """
            insert into content_topics (content_id, topic_id, relevance, created_at)
            values (:contentId, :topicId, :relevance, now())
            on conflict do nothing""", nativeQuery = true)
    int link(UUID contentId, UUID topicId, double relevance);
}
