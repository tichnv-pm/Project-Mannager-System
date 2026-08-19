package com.example.pmdaily.plan.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Một dòng change history (plan_change_histories) — PLN-AC-CHG-04: field, old/new, actor, thời gian.
 */
public record ChangeHistoryResponse(
        UUID id,
        UUID planId,
        String changeType,
        String entityType,
        UUID entityId,
        String fieldChanged,
        String oldValue,
        String newValue,
        String reason,
        UUID changeRequestId,
        UUID changedBy,
        Instant changedAt) {
}