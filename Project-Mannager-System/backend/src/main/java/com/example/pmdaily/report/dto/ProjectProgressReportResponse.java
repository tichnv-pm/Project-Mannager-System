package com.example.pmdaily.report.dto;

import java.util.List;
import java.util.UUID;

public record ProjectProgressReportResponse(
        List<ProjectProgressReportItem> items
) {
    public record ProjectProgressReportItem(
            UUID projectId,
            String code,
            String name,
            int progress,
            long totalTasks,
            long doneTasks
    ) {}
}
