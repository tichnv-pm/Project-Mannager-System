package com.example.pmdaily.audit.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        String traceId,
        UUID actorId,
        String actorUsername,
        String action,
        String entityType,
        UUID entityId,
        Map<String, Object> beforeData,
        Map<String, Object> afterData,
        Instant createdAt
) {}
