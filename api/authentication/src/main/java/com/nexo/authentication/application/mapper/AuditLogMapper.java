package com.nexo.authentication.application.mapper;

import com.nexo.authentication.domain.dto.response.AuditLogResponse;
import com.nexo.authentication.domain.entity.AuditLog;
import org.mapstruct.Mapper;

@Mapper
public interface AuditLogMapper {

    AuditLogResponse toResponse(AuditLog auditLog);
}
