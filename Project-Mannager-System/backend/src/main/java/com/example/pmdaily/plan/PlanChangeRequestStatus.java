package com.example.pmdaily.plan;

/**
 * Trạng thái change suggestion (plan_change_requests) — khớp CHECK ck_plan_chg_req_status.
 * PENDING -> APPLIED (sau khi duyệt đủ 1 hoặc 2 người tùy tổng effort) hoặc REJECTED.
 */
public enum PlanChangeRequestStatus {
    PENDING,
    APPLIED,
    REJECTED
}