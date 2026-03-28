package com.school.supervision.modules.reports;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    public void record(UUID organizationId, UUID actorUserId, String eventType, String entityType, UUID entityId, Map<String, Object> payload) {
        AuditLog auditLog = new AuditLog();
        auditLog.setOrganizationId(organizationId);
        auditLog.setActorUserId(actorUserId);
        auditLog.setEventType(eventType);
        auditLog.setEntityType(entityType);
        auditLog.setEntityId(entityId);
        auditLog.setPayloadJson(toJson(payload));
        auditLogRepository.save(auditLog);
    }

    private String toJson(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid audit payload", e);
        }
    }
}
