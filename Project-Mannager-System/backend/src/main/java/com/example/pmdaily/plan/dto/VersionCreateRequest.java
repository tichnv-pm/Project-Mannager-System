package com.example.pmdaily.plan.dto;

/**
 * Yêu cầu tạo phiên bản mới (docs/api/13-planning-api.md muc 2.5).
 */
public record VersionCreateRequest(
        String note) {
}