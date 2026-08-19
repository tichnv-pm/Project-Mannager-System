package com.example.pmdaily.plan.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.example.pmdaily.plan.ConstraintType;
import com.example.pmdaily.plan.PlanTaskStatus;
import com.example.pmdaily.plan.PlanTaskType;
import com.example.pmdaily.plan.ScheduleMode;
import com.example.pmdaily.plan.TaskPriority;
import com.example.pmdaily.task.TimeUnit;

/**
 * Sửa planning task + renumber + recalc (docs/api/13-planning-api.md muc 2.2) — PLN-FR-WBS-07.
 * Optimistic locking qua {@code version}.
 */
public record PlanTaskUpdateRequest(

        @NotBlank(message = "taskName is required")
        @Size(max = 200, message = "taskName max 200 characters")
        String taskName,

        String description,

        PlanTaskType taskType,

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

        Integer percentComplete,

        PlanTaskStatus status,

        TaskPriority priority,

        ScheduleMode scheduleMode,

        ConstraintType constraintType,

        LocalDate constraintDate,

        String phase,

        String workPackage,

        String deliverable,

        @NotNull(message = "version is required")
        Long version
) {}
