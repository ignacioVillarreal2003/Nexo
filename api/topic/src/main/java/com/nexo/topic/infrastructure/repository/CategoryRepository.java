package com.nexo.topic.infrastructure.repository;

import com.nexo.topic.domain.entity.Category;
import com.nexo.topic.domain.entity.enums.CatalogStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findAllByOrderBySortOrderAscNameAsc();

    List<Category> findByStatusOrderBySortOrderAscNameAsc(CatalogStatus status);

    Optional<Category> findBySlug(String slug);
}
