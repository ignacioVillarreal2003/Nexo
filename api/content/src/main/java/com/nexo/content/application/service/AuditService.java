package com.nexo.content.application.service;

import com.nexo.content.domain.util.RequestContext;
import com.nexo.content.domain.entity.AuditLog;
import com.nexo.content.infrastructure.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Registro de auditoría con la misma forma que el resto de servicios de la plataforma. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID userId, String userEmail, String action, String resourceType, String resourceId,
                       AuditLog.Result result, Map<String, Object> details) {
        try {
            auditLogRepository.save(AuditLog.of(userId, userEmail, action, resourceType, resourceId,
                    result,
                    MDC.get(RequestContext.CLIENT_IP_MDC_KEY),
                    MDC.get(RequestContext.USER_AGENT_MDC_KEY),
                    MDC.get(RequestContext.REQUEST_ID_MDC_KEY),
                    details == null || details.isEmpty() ? null : details));
        } catch (RuntimeException e) {
            log.error("Could not write the audit entry {} by {} on {}:{}", action, userId, resourceType,
                    resourceId, e);
        }
    }

    public static Map<String, Object> details(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            Object value = keysAndValues[i + 1];
            if (value != null) {
                map.put((String) keysAndValues[i], value);
            }
        }
        return map;
    }
}
