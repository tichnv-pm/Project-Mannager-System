package com.example.pmdaily.plan.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Change suggestion (plan_change_requests).
 */
public record ChangeSuggestionResponse(
        UUID id,
        UUID planId,
        String sourceType,
        UUID sourceId,
        String title,
        String description,
        String status,
        UUID reviewedBy,
        Instant reviewedAt,
        UUID reviewedBy2,
        Instant reviewedAt2,
        UUID createdBy,
        Instant createdAt) {
}