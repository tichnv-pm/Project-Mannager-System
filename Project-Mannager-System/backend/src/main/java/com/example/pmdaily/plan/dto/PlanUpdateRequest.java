package com.example.pmdaily.plan.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Sửa cấu hình kế hoạch (docs/api/13-planning-api.md muc 2.1 PUT /plans/{id}) — PLN-FR-PLAN-05/06.
 * Optimistic locking qua {@code version} (PLN-RULE-PLAN-06).
 */
public record PlanUpdateRequest(

        @NotBlank(message = "planName is required")
        @Size(max = 200, message = "planName max 200 characters")
        String planName,

        String description,

        UUID calendarId,

        LocalDate plannedStart,

        LocalDate plannedFinish,

        String note,

        @NotNull(message = "version is required")
        Long version
) {}
