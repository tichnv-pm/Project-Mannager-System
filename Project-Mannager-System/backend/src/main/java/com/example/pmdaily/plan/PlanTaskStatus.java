package com.example.pmdaily.plan;

/**
 * Trạng thái planning task (docs/planning/02 muc 1.4, DB ck_plan_tasks_status).
 */
public enum PlanTaskStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED,
    DELAYED,
    CANCELLED
}
