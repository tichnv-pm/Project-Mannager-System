package com.example.pmdaily.report.dto;

import java.util.List;

public record RiskIssueSummaryReportResponse(
        long openRisks,
        long openIssues,
        List<RiskLevelCount> risksByLevel,
        List<IssueSeverityCount> issuesBySeverity
) {
    public record RiskLevelCount(String level, long count) {}
    public record IssueSeverityCount(String severity, long count) {}
}
