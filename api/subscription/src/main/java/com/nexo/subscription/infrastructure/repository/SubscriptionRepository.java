package com.nexo.subscription.infrastructure.repository;

import com.nexo.subscription.domain.entity.Subscription;
import com.nexo.subscription.domain.entity.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    List<Subscription> findByUserIdAndStatusOrderBySubscribedAtAsc(UUID userId, SubscriptionStatus status);

    Optional<Subscription> findByUserIdAndTopicId(UUID userId, UUID topicId);

    long countByStatus(SubscriptionStatus status);

    @Query("select count(distinct s.userId) from Subscription s where s.status = :status")
    long countDistinctUsersByStatus(SubscriptionStatus status);

    @Query("select distinct s.userId from Subscription s where s.status = :status")
    List<UUID> findDistinctUserIdsByStatus(SubscriptionStatus status);

    /** [topicId, suscriptores] por tópico, de más a menos seguido. */
    @Query("""
            select s.topicId, count(s) from Subscription s where s.status = :status
            group by s.topicId order by count(s) desc""")
    List<Object[]> countByTopic(SubscriptionStatus status);

    @Modifying
    @Query("delete from Subscription s where s.userId = :userId")
    int deleteByUserId(UUID userId);
}
