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
 * Request cập nhật công việc — kèm version (optimistic locking, BR-GEN-08).
 * PROJECT_MEMBER chỉ được sửa task mình là assignee, giới hạn status/progress/notes (docs/05 quy tắc 2).
 */
public record TaskUpdateRequest(
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

        UUID sprintId,

        @NotNull(message = "Phiên bản không hợp lệ")
        Long version) {
}
