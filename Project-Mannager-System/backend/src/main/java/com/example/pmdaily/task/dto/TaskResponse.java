package com.example.pmdaily.task.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.example.pmdaily.task.TaskPriority;
import com.example.pmdaily.task.TaskSource;
import com.example.pmdaily.task.TaskStatus;
import com.example.pmdaily.task.TaskType;
import com.example.pmdaily.task.TimeUnit;

/**
 * Response chi tiết công việc (docs/api/05-task-api.md muc 3.3–3.4).
 */
public record TaskResponse(
        UUID id,
        String code,
        UUID projectId,
        String projectCode,
        String projectName,
        UUID parentTaskId,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        TaskType type,
        TaskSource source,
        UserBriefResponse assignee,
        UserBriefResponse reporter,
        int progress,
        boolean blocked,
        String blockerReason,
        LocalDate startDate,
        LocalDate dueDate,
        Instant actualCompletedAt,
        Integer estimateMinutes,
        TimeUnit estimateUnit,
        Integer actualMinutes,
        String notes,
        List<TagBriefResponse> tags,
        List<UserBriefResponse> collaborators,
        List<UserBriefResponse> watchers,
        long commentCount,
        long attachmentCount,
        Instant createdAt,
        Instant updatedAt,
        UUID sprintId,
        long version) {
}
