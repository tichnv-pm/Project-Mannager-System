package com.example.pmdaily.task.dto;

import java.util.UUID;

/**
 * Request đổi/gỡ sprint của task — dùng cho kéo-thả trên bảng Sprint (docs/api/05-task-api.md).
 * sprintId null để gỡ hẳn (chuyển task về Backlog, không thuộc sprint nào).
 */
public record SprintUpdateRequest(UUID sprintId) {
}
