package com.example.pmdaily.actionitem.dto;

import java.time.LocalDate;

import com.example.pmdaily.actionitem.ActionItemStatus;
import com.example.pmdaily.task.TaskPriority;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cập nhật action item (docs/api/07-action-item-api.md muc 3.5, FR-AI-02).
 * Cập nhật từng phần: PM/ADMIN sửa mọi trường; assignee chỉ status/progress (service kiểm tra).
 */
public record ActionItemUpdateRequest(
        @Size(max = 200) String title,
        String description,
        LocalDate dueDate,
        TaskPriority priority,
        ActionItemStatus status,
        @Min(0) @Max(100) Integer progress,
        @NotNull Long version) {
}
