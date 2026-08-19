package com.example.pmdaily.plan.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.example.pmdaily.plan.PlanTaskType;

/**
 * Kết quả GET /plans/{id}/critical-path (docs/api/13-planning-api.md muc 2.1, docs/planning/09 muc 3/4).
 */
public record CriticalPathResult(
        UUID planId,
        LocalDate plannedStart,
        LocalDate plannedFinish,
        Long totalDurationMinutes,
        int thresholdMinutes,
        int criticalTaskCount,
        List<CriticalTaskDto> tasks
) {

    /** Một task trong danh sách critical path — early/late dates + float (docs/planning/09 muc 3). */
    public record CriticalTaskDto(
            UUID taskId,
            String wbsCode,
            String taskName,
            PlanTaskType taskType,
            LocalDate earlyStart,
            LocalDate earlyFinish,
            LocalDate lateStart,
            LocalDate lateFinish,
            long totalFloatMinutes,
            long freeFloatMinutes,
            boolean isCritical,
            Integer criticalPathId
    ) {}
}