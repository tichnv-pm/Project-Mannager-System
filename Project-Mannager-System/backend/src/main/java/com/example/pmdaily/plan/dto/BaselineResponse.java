package com.example.pmdaily.plan.dto;

import java.time.Instant;

/**
 * Baseline plan (docs/planning/11 muc 2) — bất biến, baselineNum tăng đơn điệu.
 */
public record BaselineResponse(
        java.util.UUID id,
        java.util.UUID planId,
        int baselineNum,
        Integer versionNo,
        String description,
        Instant capturedAt,
        java.util.UUID capturedBy,
        int taskCount) {
}