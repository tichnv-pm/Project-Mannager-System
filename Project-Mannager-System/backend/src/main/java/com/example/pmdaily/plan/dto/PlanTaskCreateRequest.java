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
 * Thêm planning task vào WBS (docs/api/13-planning-api.md muc 2.2, 3.2) — PLN-FR-WBS-01.
 */
public record PlanTaskCreateRequest(

        UUID parentId,

        @NotBlank(message = "taskCode is required")
        @Size(max = 40, message = "taskCode max 40 characters")
        String taskCode,

        @NotBlank(message = "taskName is required")
        @Size(max = 200, message = "taskName max 200 characters")
        String taskName,

        @NotNull(message = "taskType is required")
        PlanTaskType taskType,

        String description,

        UUID ownerId,

        LocalDate plannedStart,

        LocalDate plannedFinish,

        Long durationMinutes,

        TimeUnit durationUnit,

        Integer plannedEffortMinutes,

        TimeUnit effortUnit,

        Integer percentComplete,

        PlanTaskStatus status,

        TaskPriority priority,

        ScheduleMode scheduleMode,

        ConstraintType constraintType,

        LocalDate constraintDate,

        String phase,

        String workPackage,

        String deliverable
) {}
