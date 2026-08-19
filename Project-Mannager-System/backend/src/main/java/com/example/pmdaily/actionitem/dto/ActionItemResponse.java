package com.example.pmdaily.actionitem.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.example.pmdaily.actionitem.ActionItemStatus;
import com.example.pmdaily.task.TaskPriority;
import com.example.pmdaily.task.dto.UserBriefResponse;

/**
 * Response action item (docs/api/07-action-item-api.md muc 3.3).
 */
public record ActionItemResponse(
        UUID id,
        UUID meetingId,
        UUID projectId,
        String title,
        String description,
        com.example.pmdaily.task.dto.UserBriefResponse assignee,
        LocalDate dueDate,
        TaskPriority priority,
        ActionItemStatus status,
        int progress,
        UUID linkedTaskId,
        Instant createdAt,
        long version) {
}
