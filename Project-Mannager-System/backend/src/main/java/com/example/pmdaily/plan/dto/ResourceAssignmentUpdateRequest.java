package com.example.pmdaily.plan.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Cập nhật allocation (docs/api/13-planning-api.md muc 2.6) — sửa % / vai trò / khoảng thời gian.
 */
public record ResourceAssignmentUpdateRequest(
        @Min(1) @Max(100) Integer allocationPercent,
        String roleOnTask,
        LocalDate startDate,
        LocalDate endDate,
        @Min(0) Integer plannedEffortMinutes) {
}