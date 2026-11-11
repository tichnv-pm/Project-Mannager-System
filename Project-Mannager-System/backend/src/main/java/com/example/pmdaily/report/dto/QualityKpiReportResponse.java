package com.example.pmdaily.report.dto;

import java.util.List;

/**
 * Báo cáo KPI chất lượng dự án (GĐ3 cải tiến).
 * - Escape rate: % Issue không gắn với 1 test case QA (test_case_id NULL) — tức không bị bắt trong
 *   quá trình QA test trước đó, "lọt" ra ngoài (production/báo cáo trực tiếp).
 * - Defect density: số Issue trên mỗi task đã hoàn thành (DONE) của dự án.
 */
public record QualityKpiReportResponse(
        long totalIssues,
        long issuesFoundInQa,
        long issuesEscaped,
        double escapeRatePercent,
        long completedTasks,
        double defectDensityPerTask,
        List<IssueSeverityCount> issuesBySeverity) {

    public record IssueSeverityCount(String severity, long count) {
    }
}
