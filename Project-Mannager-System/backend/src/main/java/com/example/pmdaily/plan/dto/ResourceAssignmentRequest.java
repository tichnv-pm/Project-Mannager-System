package com.example.pmdaily.plan.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.example.pmdaily.plan.ResourceType;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Yêu cầu gán resource vào task (docs/api/13-planning-api.md muc 2.6) — PLN-RULE-RES-01.
 */
public record ResourceAssignmentRequest(
        @NotNull ResourceType resourceType,
        @NotNull UUID resourceId,
        @Min(1) @Max(100) Integer allocationPercent,
        String roleOnTask,
        LocalDate startDate,
        LocalDate endDate,
        @Min(0) Integer plannedEffortMinutes) {

    public int allocationOrDefault() {
        return allocationPercent == null ? 100 : allocationPercent;
    }
}