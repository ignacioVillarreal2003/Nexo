package com.nexo.content.infrastructure.repository.specification;

import com.nexo.content.domain.entity.Content;
import com.nexo.content.domain.entity.ContentTopic;
import com.nexo.content.domain.entity.enums.ContentStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class ContentSpecifications {

    private ContentSpecifications() {
    }

    public static Specification<Content> search(ContentStatus status, UUID topicId, String search) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (topicId != null) {
                Subquery<UUID> linked = query.subquery(UUID.class);
                var link = linked.from(ContentTopic.class);
                Join<ContentTopic, Content> content = link.join("content");
                linked.select(content.get("id"))
                        .where(builder.equal(link.get("id").get("topicId"), topicId));
                predicates.add(root.get("id").in(linked));
            }
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("title")), like),
                        builder.like(builder.lower(root.get("originalTitle")), like),
                        builder.like(builder.lower(root.get("sourceName")), like)));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
