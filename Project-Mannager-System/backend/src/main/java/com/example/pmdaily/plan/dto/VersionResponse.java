package com.example.pmdaily.plan.dto;

import java.time.Instant;

/**
 * Phiên bản plan (docs/api/13-planning-api.md muc 2.5) — PLN-FR-VERSION-01..05.
 */
public record VersionResponse(
        java.util.UUID id,
        java.util.UUID planId,
        int versionNo,
        String status,
        String note,
        Instant createdAt,
        int taskCount,
        int dependencyCount,
        int resourceCount,
        boolean isActive) {
}