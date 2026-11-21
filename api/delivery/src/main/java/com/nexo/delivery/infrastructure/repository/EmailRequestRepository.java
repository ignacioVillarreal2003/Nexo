package com.nexo.delivery.infrastructure.repository;

import com.nexo.delivery.domain.entity.EmailRequest;
import com.nexo.delivery.domain.entity.enums.EmailRequestStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface EmailRequestRepository extends JpaRepository<EmailRequest, UUID> {

    List<EmailRequest> findByStatusOrderByRequestedAt(EmailRequestStatus status, Pageable pageable);

    boolean existsByUserIdAndDeliveryDateAndOnDemandAndStatus(UUID userId, LocalDate deliveryDate, boolean onDemand,
                                                              EmailRequestStatus status);

    @Modifying
    @Query("delete from EmailRequest r where r.userId = :userId")
    int deleteByUserId(UUID userId);
}
