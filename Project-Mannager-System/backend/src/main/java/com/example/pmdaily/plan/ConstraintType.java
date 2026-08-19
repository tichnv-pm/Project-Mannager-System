package com.example.pmdaily.plan;

/**
 * Ràng buộc lịch (docs/planning/02 muc 1.7, DB ck_plan_tasks_constraint).
 */
public enum ConstraintType {
    FIXED_DATE,
    START_NO_EARLIER_THAN,
    START_NO_LATER_THAN,
    REMOVE_SCHEDULE
}
