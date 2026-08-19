package com.example.pmdaily.plan.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.example.pmdaily.plan.PlanStatus;
import com.example.pmdaily.plan.PlanType;

/**
 * Thông tin kế hoạch trả về API (docs/api/13-planning-api.md muc 3.1).
 */
public record PlanResponse(
        UUID id,
        UUID projectId,
        String planCode,
        String planName,
        String description,
        PlanType planType,
        UUID parentPlanId,
        UUID parentMilestoneTaskId,
        UUID calendarId,
        UUID activeVersionId,
        Integer activeVersionNo,
        LocalDate plannedStart,
        LocalDate plannedFinish,
        PlanStatus status,
        int progress,
        Long durationMinutes,
        String note,
        Instant createdAt,
        long version
) {}
