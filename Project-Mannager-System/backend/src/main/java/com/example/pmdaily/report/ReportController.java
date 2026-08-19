package com.example.pmdaily.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;

import com.example.pmdaily.common.PageResponse;
import com.example.pmdaily.report.dto.ProjectProgressReportResponse;
import com.example.pmdaily.report.dto.RiskIssueSummaryReportResponse;
import com.example.pmdaily.report.dto.TasksByAssigneeReportResponse;
import com.example.pmdaily.report.dto.TasksByStatusReportResponse;
import com.example.pmdaily.security.UserPrincipal;
import com.example.pmdaily.task.dto.TaskResponse;

/**
 * REST API Report (docs/api/12-report-audit-api.md) — 6 endpoints.
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/tasks-by-status")
    @PreAuthorize("hasAuthority('report:view')")
    public TasksByStatusReportResponse tasksByStatus(
            @AuthenticationPrincipal UserPrincipal actor,
            @RequestParam UUID projectId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return reportService.getTasksByStatus(actor, projectId, fromDate, toDate);
    }

    @GetMapping("/tasks-by-assignee")
    @PreAuthorize("hasAuthority('report:view')")
    public TasksByAssigneeReportResponse tasksByAssignee(
            @AuthenticationPrincipal UserPrincipal actor,
            @RequestParam UUID projectId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return reportService.getTasksByAssignee(actor, projectId, fromDate, toDate);
    }

    @GetMapping("/overdue-tasks")
    @PreAuthorize("hasAuthority('report:view')")
    public PageResponse<TaskResponse> overdueTasks(
            @AuthenticationPrincipal UserPrincipal actor,
            @RequestParam UUID projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return reportService.getOverdueTasks(actor, projectId, page, size);
    }

    @GetMapping("/project-progress")
    @PreAuthorize("hasAuthority('report:view')")
    public ProjectProgressReportResponse projectProgress(
            @AuthenticationPrincipal UserPrincipal actor,
            @RequestParam(required = false) List<UUID> projectId) {
        return reportService.getProjectProgress(actor, projectId);
    }

    @GetMapping("/risk-issue-summary")
    @PreAuthorize("hasAuthority('report:view')")
    public RiskIssueSummaryReportResponse riskIssueSummary(
            @AuthenticationPrincipal UserPrincipal actor,
            @RequestParam UUID projectId) {
        return reportService.getRiskIssueSummary(actor, projectId);
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('report:export')")
    public void export(
            @AuthenticationPrincipal UserPrincipal actor,
            @RequestParam String report,
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            HttpServletResponse response) throws IOException {
        String filename = "report-" + report + "." + format;
        response.setContentType(MediaType.parseMediaType(
                "csv".equalsIgnoreCase(format) ? "text/csv" : "application/octet-stream").toString());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        reportService.exportReport(actor, report, format, projectId, fromDate, toDate, response.getOutputStream());
    }
}
