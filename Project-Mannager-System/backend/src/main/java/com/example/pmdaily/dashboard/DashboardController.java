package com.example.pmdaily.dashboard;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.pmdaily.dashboard.dto.DashboardSummaryResponse;
import com.example.pmdaily.dashboard.dto.ProjectProgressResponse;
import com.example.pmdaily.dashboard.dto.TaskStatsResponse;
import com.example.pmdaily.security.UserPrincipal;

/**
 * REST API Dashboard (docs/api/03-dashboard-api.md) — 3 endpoints.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public DashboardSummaryResponse summary(
            @AuthenticationPrincipal UserPrincipal actor,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return dashboardService.getSummary(actor, projectId, fromDate, toDate);
    }

    @GetMapping("/task-stats")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public TaskStatsResponse taskStats(
            @AuthenticationPrincipal UserPrincipal actor,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return dashboardService.getTaskStats(actor, projectId, fromDate, toDate);
    }

    @GetMapping("/projects/progress")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public ProjectProgressResponse projectProgress(
            @AuthenticationPrincipal UserPrincipal actor,
            @RequestParam(required = false) List<UUID> projectId) {
        return dashboardService.getProjectProgress(actor, projectId);
    }
}
