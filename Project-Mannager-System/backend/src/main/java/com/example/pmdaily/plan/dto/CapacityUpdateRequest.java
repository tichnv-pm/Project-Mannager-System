package com.example.pmdaily.plan.dto;

import java.time.LocalDate;

import com.example.pmdaily.plan.CapacitySource;
import com.example.pmdaily.plan.ResourceType;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Cập nhật capacity (docs/api/13-planning-api.md muc 2.6): resourceId lấy từ path.
 * Upsert theo khóa (resourceType, resourceId, startDate).
 */
public record CapacityUpdateRequest(
        @NotNull ResourceType resourceType,
        @Min(0) @Max(100) Integer capacityPercent,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        CapacitySource source) {
}