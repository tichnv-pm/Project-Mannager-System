package com.example.pmdaily.plan.dto;

/**
 * Yêu cầu tạo baseline (docs/api/13-planning-api.md muc 2.5) — chỉ APPROVED (PLN-RULE-BASE-01).
 */
public record BaselineCreateRequest(
        String description) {
}