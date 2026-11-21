package com.nexo.delivery.infrastructure.repository;

import com.nexo.delivery.domain.entity.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query("""
            select e from OutboxEvent e
            where e.publishedAt is null and e.attempts < :maximumAttempts
            order by e.createdAt""")
    List<OutboxEvent> findNextBatch(int maximumAttempts, Pageable pageable);

    @Modifying
    @Query("delete from OutboxEvent e where e.publishedAt < :before")
    long deleteByPublishedAtBefore(Instant before);
}
