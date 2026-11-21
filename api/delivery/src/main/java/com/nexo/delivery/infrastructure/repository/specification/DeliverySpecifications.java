package com.nexo.delivery.infrastructure.repository.specification;

import com.nexo.delivery.domain.entity.Delivery;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class DeliverySpecifications {

    private DeliverySpecifications() {
    }

    /**
     * @param from   primer día de entrega incluido (o {@code null}).
     * @param to     último día de entrega incluido (o {@code null}).
     * @param read   {@code true} solo leídas, {@code false} solo sin leer, {@code null} todas.
     */
    public static Specification<Delivery> history(UUID userId, UUID topicId, LocalDate from, LocalDate to,
                                                  Boolean read, String search, boolean favoritesOnly) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("userId"), userId));
            if (topicId != null) {
                predicates.add(builder.equal(root.get("topicId"), topicId));
            }
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("deliveryDate"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("deliveryDate"), to));
            }
            if (read != null) {
                predicates.add(read ? builder.isNotNull(root.get("readAt")) : builder.isNull(root.get("readAt")));
            }
            if (favoritesOnly) {
                predicates.add(builder.isTrue(root.get("favorite")));
            }
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("title")), like),
                        builder.like(builder.lower(root.get("summary")), like)));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
