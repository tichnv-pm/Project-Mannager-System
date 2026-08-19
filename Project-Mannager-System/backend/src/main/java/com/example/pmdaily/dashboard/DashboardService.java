package com.example.pmdaily.dashboard;

import java.time.LocalDate;

import java.util.ArrayList;
import java.util.List;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.pmdaily.dashboard.dto.DashboardSummaryResponse;
import com.example.pmdaily.dashboard.dto.ProjectProgressResponse;
import com.example.pmdaily.dashboard.dto.TaskStatsResponse;
import org.springframework.security.access.AccessDeniedException;

import com.example.pmdaily.project.ProjectMemberRepository;
import com.example.pmdaily.security.UserPrincipal;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ProjectMemberRepository memberRepository;

    public DashboardService(JdbcTemplate jdbcTemplate, ProjectMemberRepository memberRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.memberRepository = memberRepository;
    }

    public DashboardSummaryResponse getSummary(
            UserPrincipal actor,
            UUID projectId,
            LocalDate fromDate,
            LocalDate toDate) {
        List<UUID> allowedProjectIds = resolveAllowedProjects(actor, projectId);
        if (allowedProjectIds.isEmpty()) {
            return new DashboardSummaryResponse(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        LocalDate today = (fromDate != null) ? fromDate : LocalDate.now();

        String inPrjClause = createInClause(allowedProjectIds);

        String taskSql = """
                SELECT 
                    COUNT(CASE WHEN due_date = ? THEN 1 END) AS tasks_today,
                    COUNT(CASE WHEN due_date < ? AND status NOT IN ('DONE', 'CANCELLED') THEN 1 END) AS overdue_tasks,
                    COUNT(CASE WHEN due_date >= ? AND due_date <= ? AND status NOT IN ('DONE', 'CANCELLED') THEN 1 END) AS upcoming_tasks,
                    COUNT(CASE WHEN status = 'IN_PROGRESS' THEN 1 END) AS in_progress_tasks,
                    COUNT(CASE WHEN status = 'BLOCKED' THEN 1 END) AS blocked_tasks
                FROM tasks
                WHERE deleted_at IS NULL AND project_id IN (%s)
                """.formatted(inPrjClause);

        var taskStatsMap = jdbcTemplate.queryForMap(taskSql,
                today, today, today, today.plusDays(7));

        long totalTasksToday = toLong(taskStatsMap.get("tasks_today"));
        long overdueTasks = toLong(taskStatsMap.get("overdue_tasks"));
        long upcomingTasks = toLong(taskStatsMap.get("upcoming_tasks"));
        long inProgressTasks = toLong(taskStatsMap.get("in_progress_tasks"));
        long blockedTasks = toLong(taskStatsMap.get("blocked_tasks"));

        String meetingSql = """
                SELECT COUNT(*) FROM meetings
                WHERE deleted_at IS NULL AND status <> 'CANCELLED'
                  AND CAST(start_time AS DATE) = ?
                  AND project_id IN (%s)
                """.formatted(inPrjClause);
        long meetingsToday = toLong(jdbcTemplate.queryForObject(meetingSql, Long.class, today));

        String aiSql = """
                SELECT COUNT(*) FROM action_items
                WHERE deleted_at IS NULL AND status NOT IN ('DONE', 'CANCELLED')
                  AND project_id IN (%s)
                """.formatted(inPrjClause);
        long pendingActionItems = toLong(jdbcTemplate.queryForObject(aiSql, Long.class));

        String riskSql = """
                SELECT COUNT(*) FROM risks
                WHERE deleted_at IS NULL AND status IN ('OPEN', 'MONITORING')
                  AND level IN ('HIGH', 'CRITICAL')
                  AND project_id IN (%s)
                """.formatted(inPrjClause);
        long highRisks = toLong(jdbcTemplate.queryForObject(riskSql, Long.class));

        String issueSql = """
                SELECT COUNT(*) FROM issues
                WHERE deleted_at IS NULL AND status NOT IN ('RESOLVED', 'CLOSED', 'REJECTED')
                  AND project_id IN (%s)
                """.formatted(inPrjClause);
        long openIssues = toLong(jdbcTemplate.queryForObject(issueSql, Long.class));

        String milestoneSql = """
                SELECT COUNT(*) FROM milestones
                WHERE deleted_at IS NULL AND status NOT IN ('COMPLETED', 'CANCELLED')
                  AND planned_date >= ? AND planned_date <= ?
                  AND project_id IN (%s)
                """.formatted(inPrjClause);
        long upcomingMilestones = toLong(jdbcTemplate.queryForObject(milestoneSql, Long.class, today, today.plusDays(30)));

        return new DashboardSummaryResponse(
                totalTasksToday,
                overdueTasks,
                upcomingTasks,
                inProgressTasks,
                blockedTasks,
                meetingsToday,
                pendingActionItems,
                highRisks,
                openIssues,
                upcomingMilestones
        );
    }

    public TaskStatsResponse getTaskStats(
            UserPrincipal actor,
            UUID projectId,
            LocalDate fromDate,
            LocalDate toDate) {
        List<UUID> allowedProjectIds = resolveAllowedProjects(actor, projectId);
        if (allowedProjectIds.isEmpty()) {
            return new TaskStatsResponse(List.of(), List.of());
        }

        String inPrjClause = createInClause(allowedProjectIds);

        String statusSql = """
                SELECT status, COUNT(*) AS cnt FROM tasks
                WHERE deleted_at IS NULL AND project_id IN (%s)
                GROUP BY status
                """.formatted(inPrjClause);
        var statusList = jdbcTemplate.query(statusSql, (rs, rowNum) ->
                new TaskStatsResponse.StatusStat(rs.getString("status"), rs.getLong("cnt"))
        );

        String prioritySql = """
                SELECT priority, COUNT(*) AS cnt FROM tasks
                WHERE deleted_at IS NULL AND project_id IN (%s)
                GROUP BY priority
                """.formatted(inPrjClause);
        var priorityList = jdbcTemplate.query(prioritySql, (rs, rowNum) ->
                new TaskStatsResponse.PriorityStat(rs.getString("priority"), rs.getLong("cnt"))
        );

        return new TaskStatsResponse(statusList, priorityList);
    }

    public ProjectProgressResponse getProjectProgress(UserPrincipal actor, List<UUID> projectIds) {
        List<UUID> allowedProjectIds = resolveAllowedProjects(actor, null);
        if (projectIds != null && !projectIds.isEmpty()) {
            allowedProjectIds = allowedProjectIds.stream().filter(projectIds::contains).toList();
        }

        if (allowedProjectIds.isEmpty()) {
            return new ProjectProgressResponse(List.of());
        }

        String inPrjClause = createInClause(allowedProjectIds);
        String sql = """
                SELECT id, code, name, progress FROM projects
                WHERE deleted_at IS NULL AND id IN (%s)
                ORDER BY created_at DESC
                """.formatted(inPrjClause);

        var list = jdbcTemplate.query(sql, (rs, rowNum) ->
                new ProjectProgressResponse.ProjectProgressItem(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getInt("progress")
                )
        );

        return new ProjectProgressResponse(list);
    }

    private List<UUID> resolveAllowedProjects(UserPrincipal actor, UUID requestedProjectId) {
        if (actor.getRoles().contains("ADMIN")) {
            if (requestedProjectId != null) {
                return List.of(requestedProjectId);
            }
            String sql = "SELECT id FROM projects WHERE deleted_at IS NULL";
            return jdbcTemplate.query(sql, (rs, rowNum) -> UUID.fromString(rs.getString("id")));
        } else {
            String sql = """
                    SELECT DISTINCT p.id FROM projects p
                    JOIN project_members pm ON p.id = pm.project_id
                    WHERE p.deleted_at IS NULL AND pm.user_id = ?
                    """;
            List<UUID> userProjects = jdbcTemplate.query(sql, (rs, rowNum) -> UUID.fromString(rs.getString("id")), actor.getId());
            if (requestedProjectId != null) {
                if (!userProjects.contains(requestedProjectId)) {
                    throw new AccessDeniedException("Access denied to requested project");
                }
                return List.of(requestedProjectId);
            }
            return userProjects;
        }
    }

    private String createInClause(List<UUID> ids) {
        List<String> quoted = new ArrayList<>();
        for (UUID id : ids) {
            quoted.add("'" + id.toString() + "'");
        }
        return String.join(",", quoted);
    }

    private long toLong(Object val) {
        if (val == null) return 0L;
        if (val instanceof Number n) return n.longValue();
        return Long.parseLong(val.toString());
    }
}
