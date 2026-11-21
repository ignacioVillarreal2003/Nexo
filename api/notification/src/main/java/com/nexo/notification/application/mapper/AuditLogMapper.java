package com.nexo.notification.application.mapper;

import com.nexo.notification.domain.dto.response.AuditLogResponse;
import com.nexo.notification.domain.entity.AuditLog;
import org.mapstruct.Mapper;

@Mapper
public interface AuditLogMapper {

    AuditLogResponse toResponse(AuditLog entry);
}
