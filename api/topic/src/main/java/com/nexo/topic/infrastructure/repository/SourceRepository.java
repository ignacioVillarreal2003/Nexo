package com.nexo.topic.infrastructure.repository;

import com.nexo.topic.domain.entity.Source;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SourceRepository extends JpaRepository<Source, UUID> {

    List<Source> findAllByOrderByNameAsc();
}
