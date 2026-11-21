package com.nexo.delivery.infrastructure.repository;

import com.nexo.delivery.domain.entity.Delivery;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryRepository extends JpaRepository<Delivery, UUID>, JpaSpecificationExecutor<Delivery> {

    /** Contenidos entregados hace poco cuyo informe completo todavía no llegó. */
    @Query("""
            select distinct d.contentId from Delivery d
            where d.briefReady = false and d.deliveredAt > :since""")
    List<UUID> findContentIdsWithoutBrief(Instant since, Pageable pageable);

    List<Delivery> findByContentIdInAndBriefReadyFalse(Collection<UUID> contentIds);

    /** Lo del día que todavía no salió en ningún resumen automático. */
    @Query("""
            select d from Delivery d
            where d.userId = :userId and d.deliveryDate = :date and d.emailedAt is null
            order by d.deliveredAt, d.publicationDate desc""")
    List<Delivery> findNotEmailed(UUID userId, LocalDate date);

    @Modifying
    @Query("update Delivery d set d.emailedAt = :at where d.id in :ids and d.emailedAt is null")
    int markEmailed(Collection<UUID> ids, Instant at);

    interface TopicRow {
        UUID getTopicId();

        String getTopicName();

        long getTotal();
    }

    interface StatisticsRow {
        long getLast24Hours();

        long getLast7Days();

        long getRead7Days();

        long getFavorites();

        long getUsers7Days();
    }

    boolean existsByUserIdAndContentId(UUID userId, UUID contentId);

    Optional<Delivery> findByIdAndUserId(UUID id, UUID userId);

    @Query("select d.contentId from Delivery d where d.userId = :userId and d.contentId in :contentIds")
    List<UUID> findDeliveredContentIds(UUID userId, Collection<UUID> contentIds);

    /** Similitud máxima entre un embedding y lo que el usuario recibió desde {@code since}. */
    @Query(value = """
            select 1 - (embedding <=> cast(:embedding as vector))
            from deliveries
            where user_id = :userId and embedding is not null and delivered_at > :since
            order by embedding <=> cast(:embedding as vector)
            limit 1""", nativeQuery = true)
    Optional<Double> findMaximumSimilarity(UUID userId, String embedding, Instant since);

    long countByUserIdAndDeliveryDate(UUID userId, LocalDate deliveryDate);

    @Query("""
            select d from Delivery d
            where d.userId = :userId and d.deliveryDate = :date and (:topicId is null or d.topicId = :topicId)
            order by case when d.readAt is null then 0 else 1 end, d.deliveredAt, d.publicationDate desc""")
    List<Delivery> findDay(UUID userId, LocalDate date, UUID topicId);

    @Query("""
            select d.topicId as topicId, max(d.topicName) as topicName, count(d) as total from Delivery d
            where d.userId = :userId and d.deliveryDate = :date
            group by d.topicId order by max(d.topicName)""")
    List<TopicRow> findTopicsOfDay(UUID userId, LocalDate date);

    @Query(value = """
            select
              count(*) filter (where delivered_at > now() - interval '1 day') as last24Hours,
              count(*) filter (where delivered_at > now() - interval '7 days') as last7Days,
              count(*) filter (where delivered_at > now() - interval '7 days' and read_at is not null) as read7Days,
              count(*) filter (where favorite) as favorites,
              count(distinct user_id) filter (where delivered_at > now() - interval '7 days') as users7Days
            from deliveries""", nativeQuery = true)
    StatisticsRow statistics();

    @Modifying
    @Query("delete from Delivery d where d.userId = :userId")
    int deleteByUserId(UUID userId);
}
