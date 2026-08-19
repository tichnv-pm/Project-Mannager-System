package com.example.pmdaily.plan;

/**
 * Loại entity ngoài mà planning task có thể liên kết qua plan_links
 * (docs/planning/02 muc 1.9 — khớp CHECK ck_plan_links_target_type).
 */
public enum PlanLinkTargetType {
    EXECUTION_TASK,
    ISSUE,
    RISK,
    MILESTONE
}