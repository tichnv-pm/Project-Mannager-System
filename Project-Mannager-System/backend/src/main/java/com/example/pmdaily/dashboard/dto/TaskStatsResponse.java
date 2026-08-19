package com.example.pmdaily.dashboard.dto;

import java.util.List;

public record TaskStatsResponse(
        List<StatusStat> tasksByStatus,
        List<PriorityStat> tasksByPriority
) {
    public record StatusStat(String status, long count) {}
    public record PriorityStat(String priority, long count) {}
}
