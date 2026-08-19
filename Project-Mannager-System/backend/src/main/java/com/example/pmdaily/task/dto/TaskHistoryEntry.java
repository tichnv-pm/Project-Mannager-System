package com.example.pmdaily.task.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Một entry lịch sử thay đổi công việc (docs/api/05-task-api.md muc 3.14).
 * Đọc từ audit_logs (entity_type = TASK).
 */
public record TaskHistoryEntry(
        Instant changedAt,
        UUID changedBy,
        String changedByUsername,
        String action,
        Map<String, Change> changes) {

    /**
     * Giá trị trước/sau của một trường.
     */
    public record Change(Object from, Object to) {
    }
}
