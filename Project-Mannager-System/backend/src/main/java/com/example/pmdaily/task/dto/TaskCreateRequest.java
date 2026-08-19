package com.example.pmdaily.task.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.example.pmdaily.task.TaskPriority;
import com.example.pmdaily.task.TaskSource;
import com.example.pmdaily.task.TaskStatus;
import com.example.pmdaily.task.TaskType;
import com.example.pmdaily.task.TimeUnit;

/**
 * Request tạo công việc (docs/api/05-task-api.md muc 3.3, FR-TASK-01).
 */
public record TaskCreateRequest(
        @NotNull(message = "Dự án không được để trống")
        UUID projectId,

        UUID parentTaskId,

        @NotBlank(message = "Tiêu đề không được để trống")
        @Size(max = 200, message = "Tiêu đề tối đa 200 ký tự")
        String title,

        String description,

        UUID assigneeId,

        List<UUID> collaboratorIds,

        List<UUID> watcherIds,

        TaskStatus status,

        TaskPriority priority,

        TaskType type,

        TaskSource source,

        LocalDate startDate,

        LocalDate dueDate,

        @Min(value = 0, message = "Tiến độ tối thiểu 0")
        @Max(value = 100, message = "Tiến độ tối đa 100")
        Integer progress,

        Boolean blocked,

        @Size(max = 500, message = "Lý do blocker tối đa 500 ký tự")
        String blockerReason,

        @Min(value = 0, message = "Thời lượng dự kiến không âm")
        Integer estimateMinutes,

        TimeUnit estimateUnit,

        String notes,
        
        List<UUID> tagIds,
        
        UUID sprintId) {

    public TaskCreateRequest(
            UUID projectId,
            UUID parentTaskId,
            String title,
            String description,
            UUID assigneeId,
            List<UUID> collaboratorIds,
            List<UUID> watcherIds,
            TaskStatus status,
            TaskPriority priority,
            TaskType type,
            TaskSource source,
            LocalDate startDate,
            LocalDate dueDate,
            Integer progress,
            Boolean blocked,
            String blockerReason,
            Integer estimateMinutes,
            TimeUnit estimateUnit,
            String notes,
            List<UUID> tagIds) {
        this(projectId, parentTaskId, title, description, assigneeId, collaboratorIds, watcherIds, status, priority, type, source, startDate, dueDate, progress, blocked, blockerReason, estimateMinutes, estimateUnit, notes, tagIds, null);
    }
}
