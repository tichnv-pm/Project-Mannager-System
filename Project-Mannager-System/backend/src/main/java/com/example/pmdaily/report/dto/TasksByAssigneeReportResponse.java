package com.example.pmdaily.report.dto;

import java.util.List;
import java.util.UUID;

public record TasksByAssigneeReportResponse(
        List<AssigneeCountItem> items
) {
    public record AssigneeCountItem(
            UUID assigneeId,
            String fullName,
            long count,
            long doneCount
    ) {}
}
