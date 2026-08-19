package com.example.pmdaily.task.dto;

import java.util.UUID;

/**
 * Request giao việc (docs/api/05-task-api.md muc 3.7) — assigneeId null để gỡ người thực hiện.
 */
public record AssigneeUpdateRequest(UUID assigneeId) {
}
