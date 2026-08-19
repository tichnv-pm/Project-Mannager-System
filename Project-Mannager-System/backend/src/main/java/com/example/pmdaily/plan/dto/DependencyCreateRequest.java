package com.example.pmdaily.plan.dto;

import com.example.pmdaily.plan.DependencyType;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Tạo dependency giữa predecessorTaskId và task hiện tại (taskId trong path = successor) —
 * docs/api/13-planning-api.md muc 2.3, PLN-FR-DEP-01.
 */
public record DependencyCreateRequest(
        @NotNull UUID predecessorTaskId,
        @NotNull DependencyType dependencyType,
        @Min(-100000) Integer lagMinutes
) {

    public int lag() {
        return lagMinutes == null ? 0 : lagMinutes;
    }
}