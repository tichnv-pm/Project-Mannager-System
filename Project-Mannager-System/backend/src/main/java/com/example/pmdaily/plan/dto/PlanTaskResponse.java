package com.example.pmdaily.plan.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.example.pmdaily.plan.ConstraintType;
import com.example.pmdaily.plan.PlanTaskStatus;
import com.example.pmdaily.plan.PlanTaskType;
import com.example.pmdaily.plan.ScheduleMode;
import com.example.pmdaily.plan.TaskPriority;
import com.example.pmdaily.task.TimeUnit;

/**
 * Thông tin planning task trả về API (docs/api/13-planning-api.md muc 3.3 — task node).
 */
public record PlanTaskResponse(
        UUID id,
        UUID planId,
        UUID parentId,
        String wbsCode,
        String taskCode,
        String taskName,
        String description,
        PlanTaskType taskType,
        int outlineLevel,
        int sequenceNumber,
        String phase,
        String workPackage,
        String deliverable,
        UUID ownerId,
        LocalDate plannedStart,
        LocalDate plannedFinish,
        Long durationMinutes,
        TimeUnit durationUnit,
        Integer plannedEffortMinutes,
        TimeUnit effortUnit,
        LocalDate actualStart,
        LocalDate actualFinish,
        Integer actualEffortMinutes,
        Integer remainingEffortMinutes,
        int percentComplete,
        PlanTaskStatus status,
        TaskPriority priority,
        ScheduleMode scheduleMode,
        ConstraintType constraintType,
        LocalDate constraintDate,
        boolean isSummary,
        boolean isMilestone,
        boolean isCritical,
        Instant createdAt,
        long version
) {}
