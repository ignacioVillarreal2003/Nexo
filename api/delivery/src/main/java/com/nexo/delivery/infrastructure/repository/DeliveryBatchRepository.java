package com.nexo.delivery.infrastructure.repository;

import com.nexo.delivery.domain.entity.DeliveryBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface DeliveryBatchRepository extends JpaRepository<DeliveryBatch, DeliveryBatch.Id> {

    @Modifying
    @Query("delete from DeliveryBatch b where b.id.userId = :userId")
    int deleteByUserId(UUID userId);
}
