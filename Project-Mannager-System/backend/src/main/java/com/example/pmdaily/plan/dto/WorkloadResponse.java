package com.example.pmdaily.plan.dto;

import java.time.LocalDate;
import java.util.List;

import com.example.pmdaily.plan.ResourceType;

import jakarta.validation.constraints.NotBlank;

/**
 * Workload của một resource theo granularity (docs/api/13-planning-api.md muc 2.6) — PLN-FR-RES-03/04.
 */
public record WorkloadResponse(
        ResourceType resourceType,
        java.util.UUID resourceId,
        String resourceName,
        @NotBlank String granularity,
        LocalDate from,
        LocalDate to,
        long totalDemandMinutes,
        Integer totalCapacityMinutes,
        Double totalUtilizationPercent,
        boolean overAllocation,
        List<WorkloadBucket> buckets) {
}