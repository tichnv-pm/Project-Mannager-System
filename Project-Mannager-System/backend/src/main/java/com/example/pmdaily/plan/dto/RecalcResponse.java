package com.example.pmdaily.plan.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.example.pmdaily.plan.SchedulingWarningType;

/**
 * Kết quả POST /plans/{id}/recalc (docs/api/13-planning-api.md muc 2.1, docs/planning/08 muc 4/5).
 */
public record RecalcResponse(
        UUID planId,
        LocalDate plannedStart,
        LocalDate plannedFinish,
        Long durationMinutes,
        int totalTasks,
        int scheduledTasks,
        List<SchedulingWarningDto> warnings
) {

    /** Một cảnh báo của scheduling engine. */
    public record SchedulingWarningDto(
            String wbsCode,
            SchedulingWarningType type,
            String message
    ) {}
}
