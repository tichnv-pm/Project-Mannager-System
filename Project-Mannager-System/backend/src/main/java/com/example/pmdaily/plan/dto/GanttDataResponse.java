package com.example.pmdaily.plan.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.example.pmdaily.plan.DependencyType;
import com.example.pmdaily.plan.PlanTaskType;

/**
 * Dữ liệu Gantt UI (docs/api/13-planning-api.md muc 3.3, docs/planning/13 muc 5) — read-only,
 * tính live mỗi lần gọi: critical qua CriticalPathService, baseline overlay lấy baseline mới nhất.
 */
public record GanttDataResponse(
        GanttPlanBrief plan,
        List<GanttTaskResponse> tasks,
        List<GanttDependencyResponse> dependencies,
        List<GanttSprintResponse> sprints,
        List<String> warnings) {

    public record GanttSprintResponse(
            UUID id,
            String sprintName,
            LocalDate startDate,
            LocalDate endDate,
            String status) {
    }

    public record GanttPlanBrief(
            UUID id,
            String planCode,
            String planName,
            String planType,
            String status) {
    }

    public record GanttBaseline(
            LocalDate start,
            LocalDate finish) {
    }

    public record GanttResource(
            UUID resourceId,
            String resourceType,
            Integer allocationPercent) {
    }

    public record GanttTaskResponse(
            UUID id,
            UUID parentId,
            String wbsCode,
            String taskName,
            PlanTaskType taskType,
            LocalDate start,
            LocalDate finish,
            Long durationMinutes,
            Integer plannedEffortMinutes,
            int percentComplete,
            String status,
            String scheduleMode,
            boolean isCritical,
            GanttBaseline baseline,
            List<GanttResource> resources,
            boolean hasGitCommits,
            boolean hasGitPrs) {
    }

    public record GanttDependencyResponse(
            UUID from,
            UUID to,
            DependencyType type,
            Integer lagMinutes) {
    }
}