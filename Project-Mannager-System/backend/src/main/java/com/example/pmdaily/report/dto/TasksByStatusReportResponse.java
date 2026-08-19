package com.example.pmdaily.report.dto;

import java.util.List;

public record TasksByStatusReportResponse(
        List<StatusCountItem> items
) {
    public record StatusCountItem(String status, long count) {}
}
