package com.nexo.delivery.infrastructure.repository;

import com.nexo.delivery.domain.entity.DeliveryRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeliveryRunRepository extends JpaRepository<DeliveryRun, UUID> {

    Page<DeliveryRun> findAllByOrderByStartedAtDesc(Pageable pageable);
}
