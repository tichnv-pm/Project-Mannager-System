package com.example.pmdaily.plan;

/**
 * Trạng thái kế hoạch (docs/planning/02 muc 1.2) — vòng đời:
 * DRAFT → SUBMITTED → APPROVED → ACTIVE → (ON_HOLD | COMPLETED | CANCELLED | ARCHIVED).
 */
public enum PlanStatus {
    DRAFT,
    SUBMITTED,
    APPROVED,
    ACTIVE,
    ON_HOLD,
    COMPLETED,
    CANCELLED,
    ARCHIVED
}
