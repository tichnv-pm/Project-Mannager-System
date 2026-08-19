package com.example.pmdaily.report;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.pmdaily.audit.AuditService;
import com.example.pmdaily.common.ErrorCode;
import com.example.pmdaily.common.PageResponse;
import org.springframework.security.access.AccessDeniedException;
import com.example.pmdaily.exception.BusinessException;
import com.example.pmdaily.project.ProjectMemberRepository;
import com.example.pmdaily.report.dto.ProjectProgressReportResponse;
import com.example.pmdaily.report.dto.RiskIssueSummaryReportResponse;
import com.example.pmdaily.report.dto.TasksByAssigneeReportResponse;
import com.example.pmdaily.report.dto.TasksByStatusReportResponse;
import com.example.pmdaily.security.UserPrincipal;
import com.example.pmdaily.task.Task;
import com.example.pmdaily.task.TaskRepository;
import com.example.pmdaily.task.TaskSpecification;
import com.example.pmdaily.task.dto.TaskResponse;
import com.example.pmdaily.task.mapper.TaskMapper;

@Service
@Transactional(readOnly = true)
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ProjectMemberRepository memberRepository;
    private final TaskRepository taskRepository;
    private final TaskMapper taskMapper;
    private final AuditService auditService;

    public ReportService(
            JdbcTemplate jdbcTemplate,
            ProjectMemberRepository memberRepository,
            TaskRepository taskRepository,
            TaskMapper taskMapper,
            AuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.memberRepository = memberRepository;
        this.taskRepository = taskRepository;
        this.taskMapper = taskMapper;
        this.auditService = auditService;
    }

    public TasksByStatusReportResponse getTasksByStatus(
            UserPrincipal actor,
            UUID projectId,
            LocalDate fromDate,
            LocalDate toDate) {
        checkProjectAccess(actor, projectId);

        String sql = """
                SELECT status, COUNT(*) AS cnt FROM tasks
                WHERE deleted_at IS NULL AND project_id = ?
                GROUP BY status
                """;
        var items = jdbcTemplate.query(sql, (rs, rowNum) ->
                new TasksByStatusReportResponse.StatusCountItem(rs.getString("status"), rs.getLong("cnt")),
                projectId
        );
        return new TasksByStatusReportResponse(items);
    }

    public TasksByAssigneeReportResponse getTasksByAssignee(
            UserPrincipal actor,
            UUID projectId,
            LocalDate fromDate,
            LocalDate toDate) {
        checkProjectAccess(actor, projectId);

        String sql = """
                SELECT u.id AS assignee_id, u.full_name,
                       COUNT(t.id) AS cnt,
                       COUNT(CASE WHEN t.status = 'DONE' THEN 1 END) AS done_cnt
                FROM tasks t
                JOIN users u ON t.assignee_id = u.id
                WHERE t.deleted_at IS NULL AND t.project_id = ?
                GROUP BY u.id, u.full_name
                """;
        var items = jdbcTemplate.query(sql, (rs, rowNum) ->
                new TasksByAssigneeReportResponse.AssigneeCountItem(
                        UUID.fromString(rs.getString("assignee_id")),
                        rs.getString("full_name"),
                        rs.getLong("cnt"),
                        rs.getLong("done_cnt")
                ),
                projectId
        );
        return new TasksByAssigneeReportResponse(items);
    }

    public PageResponse<TaskResponse> getOverdueTasks(
            UserPrincipal actor,
            UUID projectId,
            int page,
            int size) {
        checkProjectAccess(actor, projectId);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "dueDate"));
        Specification<Task> spec = Specification.where(TaskSpecification.notDeleted())
                .and(TaskSpecification.projectId(projectId))
                .and((root, query, cb) -> cb.and(
                        cb.lessThan(root.get("dueDate"), LocalDate.now()),
                        root.get("status").in(com.example.pmdaily.task.TaskStatus.DONE, com.example.pmdaily.task.TaskStatus.CANCELLED).not()
                ));

        var taskPage = taskRepository.findAll(spec, pageable);
        return PageResponse.of(taskPage, taskMapper::toResponse);
    }

    public ProjectProgressReportResponse getProjectProgress(
            UserPrincipal actor,
            List<UUID> projectIds) {
        List<UUID> allowedProjectIds = resolveAllowedProjects(actor, null);
        if (projectIds != null && !projectIds.isEmpty()) {
            allowedProjectIds = allowedProjectIds.stream().filter(projectIds::contains).toList();
        }

        if (allowedProjectIds.isEmpty()) {
            return new ProjectProgressReportResponse(List.of());
        }

        String inClause = createInClause(allowedProjectIds);
        String sql = """
                SELECT p.id, p.code, p.name, p.progress,
                       COUNT(t.id) AS total_tasks,
                       COUNT(CASE WHEN t.status = 'DONE' THEN 1 END) AS done_tasks
                FROM projects p
                LEFT JOIN tasks t ON p.id = t.project_id AND t.deleted_at IS NULL
                WHERE p.deleted_at IS NULL AND p.id IN (%s)
                GROUP BY p.id, p.code, p.name, p.progress
                """.formatted(inClause);

        var items = jdbcTemplate.query(sql, (rs, rowNum) ->
                new ProjectProgressReportResponse.ProjectProgressReportItem(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getInt("progress"),
                        rs.getLong("total_tasks"),
                        rs.getLong("done_tasks")
                )
        );
        return new ProjectProgressReportResponse(items);
    }

    public RiskIssueSummaryReportResponse getRiskIssueSummary(
            UserPrincipal actor,
            UUID projectId) {
        checkProjectAccess(actor, projectId);

        String openRisksSql = "SELECT COUNT(*) FROM risks WHERE deleted_at IS NULL AND project_id = ? AND status IN ('OPEN', 'MONITORING')";
        long openRisks = toLong(jdbcTemplate.queryForObject(openRisksSql, Long.class, projectId));

        String openIssuesSql = "SELECT COUNT(*) FROM issues WHERE deleted_at IS NULL AND project_id = ? AND status NOT IN ('RESOLVED', 'CLOSED', 'REJECTED')";
        long openIssues = toLong(jdbcTemplate.queryForObject(openIssuesSql, Long.class, projectId));

        String riskLevelSql = "SELECT level, COUNT(*) AS cnt FROM risks WHERE deleted_at IS NULL AND project_id = ? GROUP BY level";
        var risksByLevel = jdbcTemplate.query(riskLevelSql, (rs, rowNum) ->
                new RiskIssueSummaryReportResponse.RiskLevelCount(rs.getString("level"), rs.getLong("cnt")),
                projectId
        );

        String issueSeveritySql = "SELECT severity, COUNT(*) AS cnt FROM issues WHERE deleted_at IS NULL AND project_id = ? GROUP BY severity";
        var issuesBySeverity = jdbcTemplate.query(issueSeveritySql, (rs, rowNum) ->
                new RiskIssueSummaryReportResponse.IssueSeverityCount(rs.getString("severity"), rs.getLong("cnt")),
                projectId
        );

        return new RiskIssueSummaryReportResponse(openRisks, openIssues, risksByLevel, issuesBySeverity);
    }

    @Transactional
    public void exportReport(
            UserPrincipal actor,
            String reportType,
            String format,
            UUID projectId,
            LocalDate fromDate,
            LocalDate toDate,
            OutputStream output) {
        if (reportType == null || reportType.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "reportType is required");
        }

        auditService.record("REPORT_EXPORTED", "REPORT", null,
                Map.of("reportType", reportType, "format", format != null ? format : "csv"));

        PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8), true);

        writer.println("Report: " + reportType);
        writer.println("Exported Date: " + LocalDate.now());
        writer.println("Exported By: " + actor.getUsername());
        writer.println();

        switch (reportType) {
            case "tasks-by-status" -> {
                if (projectId == null) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "projectId is required");
                var report = getTasksByStatus(actor, projectId, fromDate, toDate);
                writer.println("Status,Count");
                for (var item : report.items()) {
                    writer.println(item.status() + "," + item.count());
                }
            }
            case "tasks-by-assignee" -> {
                if (projectId == null) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "projectId is required");
                var report = getTasksByAssignee(actor, projectId, fromDate, toDate);
                writer.println("Assignee,Total Tasks,Done Tasks");
                for (var item : report.items()) {
                    writer.println("\"" + item.fullName() + "\"," + item.count() + "," + item.doneCount());
                }
            }
            case "project-progress" -> {
                var report = getProjectProgress(actor, projectId != null ? List.of(projectId) : null);
                writer.println("Code,Name,Progress (%),Total Tasks,Done Tasks");
                for (var item : report.items()) {
                    writer.println(item.code() + ",\"" + item.name() + "\"," + item.progress() + "," + item.totalTasks() + "," + item.doneTasks());
                }
            }
            case "risk-issue-summary" -> {
                if (projectId == null) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "projectId is required");
                var report = getRiskIssueSummary(actor, projectId);
                writer.println("Open Risks: " + report.openRisks());
                writer.println("Open Issues: " + report.openIssues());
            }
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Unsupported report type: " + reportType);
        }

        writer.flush();
    }

    private void checkProjectAccess(UserPrincipal actor, UUID projectId) {
        if (projectId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "projectId is required");
        }
        if (actor.getRoles().contains("ADMIN")) {
            return;
        }
        if (!memberRepository.existsByProjectIdAndUser_Id(projectId, actor.getId())) {
            throw new AccessDeniedException("Access denied to project");
        }
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
