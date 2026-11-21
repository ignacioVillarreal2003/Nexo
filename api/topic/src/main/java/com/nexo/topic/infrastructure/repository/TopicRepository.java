package com.nexo.topic.infrastructure.repository;

import com.nexo.topic.domain.entity.Topic;
import com.nexo.topic.domain.entity.enums.CatalogStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TopicRepository extends JpaRepository<Topic, UUID> {

    @EntityGraph(attributePaths = {"category", "sources"})
    @Query("select distinct t from Topic t order by t.name")
    List<Topic> findAllForAdministration();

    /** Tópicos visibles en el catálogo: activos y en una categoría activa. */
    @EntityGraph(attributePaths = "category")
    @Query("""
            select t from Topic t
            where t.status = :status and t.category.status = :status
            order by t.category.sortOrder, t.name""")
    List<Topic> findPublished(CatalogStatus status);

    @EntityGraph(attributePaths = {"category", "sources"})
    @Query("""
            select distinct t from Topic t
            where t.status = :status and t.category.status = :status""")
    List<Topic> findPublishedWithSources(CatalogStatus status);

    @EntityGraph(attributePaths = {"category", "sources"})
    Optional<Topic> findDetailedById(UUID id);

    @EntityGraph(attributePaths = "category")
    Optional<Topic> findBySlug(String slug);

    @EntityGraph(attributePaths = "category")
    List<Topic> findByIdIn(Collection<UUID> ids);
}
