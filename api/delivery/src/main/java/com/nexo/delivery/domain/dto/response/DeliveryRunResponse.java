package com.nexo.delivery.domain.dto.response;

import com.nexo.delivery.domain.entity.enums.RunStatus;
import com.nexo.delivery.domain.entity.enums.RunTrigger;

import java.time.Instant;
import java.util.UUID;

public record DeliveryRunResponse(UUID id, RunTrigger trigger, RunStatus status, Instant startedAt,
                                  Instant finishedAt, int usersProcessed, int deliveriesCreated, int errors,
                                  String notes) {
}
