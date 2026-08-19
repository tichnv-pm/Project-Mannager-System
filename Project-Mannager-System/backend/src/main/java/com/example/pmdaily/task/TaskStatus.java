package com.example.pmdaily.task;

/**
 * Trạng thái công việc (bảng tasks.status — docs/api/05-task-api.md muc 1.1).
 */
public enum TaskStatus {
    TODO,
    IN_PROGRESS,
    BLOCKED,
    REVIEW,
    DONE,
    CANCELLED
}
