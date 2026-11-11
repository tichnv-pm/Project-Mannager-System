package com.example.pmdaily.task.dto;

import java.util.UUID;

/**
 * Request đổi/gỡ parent (Epic ↔ Story/Task) — docs/api/05-task-api.md, dùng cho màn hình Backlog
 * kéo-thả (GĐ3 cải tiến). parentTaskId null để gỡ hẳn parent (chuyển về "Chưa gán Epic").
 */
public record ParentUpdateRequest(UUID parentTaskId) {
}
