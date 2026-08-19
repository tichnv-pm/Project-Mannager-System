package com.example.pmdaily.plan.dto;

import java.util.List;

/**
 * Kết quả so sánh variance của 1 baseline với current (docs/planning/11 muc 3).
 */
public record BaselineVarianceResponse(
        java.util.UUID baselineId,
        int baselineNum,
        java.util.UUID planId,
        String planName,
        List<BaselineVarianceRow> tasks) {
}