package com.example.pmdaily.plan;

/**
 * Phân loại liên kết plan_links (docs/planning/02 muc 1.10 — PLN-RULE-LINK-*).
 * BLOCKED_BY chỉ áp dụng cho Issue/Risk (PLN-FR-LINK-06).
 */
public enum PlanLinkType {
    RELATED,
    BLOCKED_BY
}