package com.example.pmdaily.plan;

/**
 * Loại node WBS (docs/planning/02 muc 1.3, docs/planning/07 muc 1).
 * Lá (không có con): TASK, MILESTONE, EXTERNAL_TASK. Có thể có con: PHASE, SUMMARY_TASK, WORK_PACKAGE.
 */
public enum PlanTaskType {
    PHASE,
    SUMMARY_TASK,
    WORK_PACKAGE,
    TASK,
    MILESTONE,
    EXTERNAL_TASK;

    public boolean isLeaf() {
        return this == TASK || this == MILESTONE || this == EXTERNAL_TASK;
    }
}
