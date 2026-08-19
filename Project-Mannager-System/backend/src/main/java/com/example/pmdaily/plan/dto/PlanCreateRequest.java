package com.example.pmdaily.plan.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.example.pmdaily.plan.PlanType;

/**
 * Tạo kế hoạch (docs/api/13-planning-api.md muc 3.1) — PLN-FR-PLAN-01/02.
 */
public record PlanCreateRequest(

        @NotNull(message = "projectId is required")
        UUID projectId,

        @NotBlank(message = "planCode is required")
        @Size(min = 3, max = 50, message = "planCode length 3-50")
        String planCode,

        @NotBlank(message = "planName is required")
        @Size(max = 200, message = "planName max 200 characters")
        String planName,

        @NotNull(message = "planType is required")
        PlanType planType,

        UUID parentPlanId,

        UUID parentMilestoneTaskId,

        UUID calendarId,

        LocalDate plannedStart,

        LocalDate plannedFinish,

        String description
) {}
