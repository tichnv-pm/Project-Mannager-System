package com.example.pmdaily.actionitem.dto;

import java.time.LocalDate;

import com.example.pmdaily.task.TaskPriority;

/**
 * Chuyển action item thành task (docs/api/07-action-item-api.md muc 3.7, FR-AI-03).
 * dueDate/priority tùy chọn — mặc định lấy từ action item.
 */
public record ConvertToTaskRequest(
        LocalDate dueDate,
        TaskPriority priority) {
}
