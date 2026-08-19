package com.example.pmdaily.dashboard.dto;

public record DashboardSummaryResponse(
        long totalTasksToday,
        long overdueTasks,
        long upcomingTasks,
        long inProgressTasks,
        long blockedTasks,
        long meetingsToday,
        long pendingActionItems,
        long highRisks,
        long openIssues,
        long upcomingMilestones
) {}
