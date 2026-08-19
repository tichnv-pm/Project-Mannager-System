package com.example.pmdaily.plan.dto;

import java.time.LocalDate;

/**
 * Workload theo bucket ngày/tuần/tháng (docs/planning/10 muc 4).
 * capacityMinutes = null với EXTERNAL (capacity vô hạn, không over) — PLN-RULE-RES-03.
 */
public record WorkloadBucket(
        LocalDate date,
        long demandMinutes,
        Integer capacityMinutes,
        Double utilizationPercent,
        boolean overAllocation) {
}