package com.nexo.content.infrastructure.repository;

import com.nexo.content.domain.entity.PipelineError;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PipelineErrorRepository extends JpaRepository<PipelineError, UUID> {

    Page<PipelineError> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<PipelineError> findByRunIdOrderByCreatedAtDesc(UUID runId, Pageable pageable);
}
