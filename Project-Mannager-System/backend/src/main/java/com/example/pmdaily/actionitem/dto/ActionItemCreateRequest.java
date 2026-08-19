package com.example.pmdaily.actionitem.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.example.pmdaily.task.TaskPriority;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Tạo action item (docs/api/07-action-item-api.md muc 3.3, FR-AI-01).
 * BR-AI-01 project phải khớp meeting; BR-AI-02 assignee thuộc project (kiểm tra trong service).
 */
public record ActionItemCreateRequest(
        @NotNull UUID meetingId,
        @NotNull UUID projectId,
        @NotBlank @Size(max = 200) String title,
        String description,
        @NotNull UUID assigneeId,
        LocalDate dueDate,
        TaskPriority priority) {
}
