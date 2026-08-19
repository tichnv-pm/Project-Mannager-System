package com.example.pmdaily.plan.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.example.pmdaily.plan.PlanTaskType;

/**
 * Variance 1 task: Current vs Baseline (docs/planning/11 muc 3) — PLN-AC-BASE-04.
 */
public record BaselineVarianceRow(
        UUID taskId,
        String wbsCode,
        String taskName,
        PlanTaskType taskType,
        LocalDate baselineStart,
        LocalDate baselineFinish,
        LocalDate currentStart,
        LocalDate currentFinish,
        Integer baselineDurationMinutes,
        Integer currentDurationMinutes,
        Integer baselineEffortMinutes,
        Integer currentEffortMinutes,
        int baselineProgress,
        int currentProgress,
        Long startDifferenceDays,
        Long finishDifferenceDays,
        Long durationDifferenceMinutes,
        Long effortDifferenceMinutes,
        int progressDifference,
        boolean milestoneDone,
        boolean taskDeleted) {
}