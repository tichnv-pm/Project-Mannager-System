package com.example.pmdaily.task.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.example.pmdaily.task.TaskPriority;
import com.example.pmdaily.task.TaskStatus;
import com.example.pmdaily.task.TaskType;

/**
 * Response tóm tắt công việc cho danh sách (docs/api/05-task-api.md muc 3.1).
 */
public record TaskSummaryResponse(
        UUID id,
        String code,
        UUID projectId,
        String projectCode,
        String projectName,
        UUID parentTaskId,
        String title,
        TaskStatus status,
        TaskPriority priority,
        TaskType type,
        LocalDate dueDate,
        int progress,
        boolean blocked,
        UserBriefResponse assignee,
        Instant createdAt,
        Instant updatedAt,
        UUID sprintId,
        long version) {
}
