package com.nexo.content.infrastructure.repository;

import com.nexo.content.domain.entity.PipelineRun;
import com.nexo.content.domain.entity.enums.RunStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

public interface PipelineRunRepository extends JpaRepository<PipelineRun, UUID> {

    Page<PipelineRun> findAllByOrderByStartedAtDesc(Pageable pageable);

    @Modifying
    @Query("""
            update PipelineRun r set r.status = :failed, r.finishedAt = :now,
                   r.notes = 'Interrupted: the collector never closed this run'
            where r.status = :running and r.startedAt < :before""")
    int failStale(RunStatus running, RunStatus failed, Instant before, Instant now);
}
