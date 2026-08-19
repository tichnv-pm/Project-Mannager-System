package com.example.pmdaily.task.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

import com.example.pmdaily.task.TaskStatus;

/**
 * Request chuyển trạng thái công việc (docs/api/05-task-api.md muc 3.8).
 */
public record StatusUpdateRequest(
        @NotNull(message = "Trạng thái không được để trống")
        TaskStatus status,

        String blockerReason) {
}
