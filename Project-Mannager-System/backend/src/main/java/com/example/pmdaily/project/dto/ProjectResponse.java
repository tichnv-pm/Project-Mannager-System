package com.example.pmdaily.project.dto;

import java.time.Instant;
import java.util.UUID;

import com.example.pmdaily.project.ProjectStatus;

/**
 * Response dự án (docs/api/04-project-api.md muc 3.2).
 */
public record ProjectResponse(
        UUID id,
        String code,
        String name,
        String description,
        ProjectStatus status,
        java.time.LocalDate startDate,
        java.time.LocalDate endDate,
        UUID projectManagerId,
        String customerName,
        int progress,
        long memberCount,
        Instant createdAt,
        long version) {
}
