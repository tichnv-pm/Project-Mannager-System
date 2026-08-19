package com.example.pmdaily.plan;

/**
 * Loại cảnh báo của scheduling engine (docs/planning/08 muc 5) — PLN-FR-SCHED-05/06.
 */
public enum SchedulingWarningType {

    /** Constraint xung khắc dependency (giữ candidate, chỉ cảnh báo). */
    CONSTRAINT_CONFLICT,

    /** Start ban đầu rơi vào ngày nghỉ/lễ — bị đẩy sang ngày làm việc kế tiếp. */
    DATE_NOT_WORKING,

    /** Lag âm được phép (docs/planning/08 muc 3.2, PLN-AC-DEP-05). */
    NEGATIVE_LAG,

    /** Task AUTO không có predecessor, không có plan start và chưa có ngày — chưa thể lập lịch. */
    NO_START_ANCHOR,

    /** Vòng lặp dependency phát hiện lúc recalc (không xảy ra nếu đã validate khi tạo). */
    CYCLE_DEPENDENCY
}
