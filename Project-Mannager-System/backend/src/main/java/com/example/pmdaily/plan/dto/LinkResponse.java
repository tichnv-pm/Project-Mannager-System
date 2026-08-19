package com.example.pmdaily.plan.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Liên kết planning task ↔ entity ngoài (plan_links).
 */
public record LinkResponse(
        UUID id,
        UUID planId,
        UUID planningTaskId,
        String targetType,
        UUID targetId,
        String linkType,
        String note,
        boolean isPrimaryExecution,
        UUID createdBy,
        Instant createdAt) {
}