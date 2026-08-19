package com.example.pmdaily.plan;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.pmdaily.exception.ResourceNotFoundException;
import com.example.pmdaily.plan.dto.CriticalPathResult;
import com.example.pmdaily.plan.dto.GanttDataResponse;
import com.example.pmdaily.plan.dto.GanttDataResponse.GanttBaseline;
import com.example.pmdaily.plan.dto.GanttDataResponse.GanttDependencyResponse;
import com.example.pmdaily.plan.dto.GanttDataResponse.GanttPlanBrief;
import com.example.pmdaily.plan.dto.GanttDataResponse.GanttResource;
import com.example.pmdaily.plan.dto.GanttDataResponse.GanttTaskResponse;
import com.example.pmdaily.project.ProjectMemberRepository;
import com.example.pmdaily.security.UserPrincipal;

/**
 * Gantt data read-only (docs/api/13-planning-api.md muc 3.3, docs/planning/13 muc 5) — PLN-FE-10.
 * Tính live mỗi lần gọi: critical path (not saved), baseline overlay lấy baseline mới nhất (bất biến).
 * Warnings = [] vì read-only (warnings sinh từ POST /recalc — Scheduling tab).
 */
@Service
@Transactional(readOnly = true)
public class PlanGanttService {

    private final ProjectPlanRepository planRepository;
    private final PlanTaskRepository taskRepository;
    private final PlanTaskDependencyRepository dependencyRepository;
    private final PlanTaskResourceRepository resourceRepository;
    private final PlanBaselineRepository baselineRepository;
    private final PlanBaselineTaskRepository baselineTaskRepository;
    private final CriticalPathService criticalPathService;
    private final ProjectMemberRepository memberRepository;
    private final com.example.pmdaily.sprint.SprintRepository sprintRepository;
    private final com.example.pmdaily.task.TaskRepository regularTaskRepository;
    private final com.example.pmdaily.git.GitCommitRepository gitCommitRepository;
    private final com.example.pmdaily.git.GitPullRequestRepository gitPullRequestRepository;

    public PlanGanttService(ProjectPlanRepository planRepository,
            PlanTaskRepository taskRepository,
            PlanTaskDependencyRepository dependencyRepository,
            PlanTaskResourceRepository resourceRepository,
            PlanBaselineRepository baselineRepository,
            PlanBaselineTaskRepository baselineTaskRepository,
            CriticalPathService criticalPathService,
            ProjectMemberRepository memberRepository,
            com.example.pmdaily.sprint.SprintRepository sprintRepository,
            com.example.pmdaily.task.TaskRepository regularTaskRepository,
            com.example.pmdaily.git.GitCommitRepository gitCommitRepository,
            com.example.pmdaily.git.GitPullRequestRepository gitPullRequestRepository) {
        this.planRepository = planRepository;
        this.taskRepository = taskRepository;
        this.dependencyRepository = dependencyRepository;
        this.resourceRepository = resourceRepository;
        this.baselineRepository = baselineRepository;
        this.baselineTaskRepository = baselineTaskRepository;
        this.criticalPathService = criticalPathService;
        this.memberRepository = memberRepository;
        this.sprintRepository = sprintRepository;
        this.regularTaskRepository = regularTaskRepository;
        this.gitCommitRepository = gitCommitRepository;
        this.gitPullRequestRepository = gitPullRequestRepository;
    }

    public GanttDataResponse build(UserPrincipal actor, UUID planId) {
        ProjectPlan plan = planRepository.findByIdAndDeletedAtIsNull(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Kế hoạch", planId));
        checkProjectViewAccess(actor, plan.getProject().getId());

        List<PlanTask> tasks = taskRepository.findByPlanIdAndDeletedAtIsNull(planId).stream()
                .sorted(Comparator.comparingInt(PlanTask::getOutlineLevel)
                        .thenComparingInt(PlanTask::getSequenceNumber))
                .toList();

        Map<UUID, Boolean> critical = criticalFlags(planId);
        Map<UUID, GanttBaseline> baseline = baselineOverlay(plan);
        Map<UUID, List<GanttResource>> resources = resourceMap(planId);

        // Fetch regular tasks to match git commits/PRs
        List<com.example.pmdaily.task.Task> regularTasks = regularTaskRepository.findByProjectIdAndDeletedAtIsNull(plan.getProject().getId());
        Map<String, com.example.pmdaily.task.Task> regularTaskMap = new HashMap<>();
        for (com.example.pmdaily.task.Task t : regularTasks) {
            if (t.getCode() != null) {
                regularTaskMap.put(t.getCode(), t);
            }
        }

        List<GanttTaskResponse> taskDtos = tasks.stream()
                .map(t -> {
                    com.example.pmdaily.task.Task matched = regularTaskMap.get(t.getTaskCode());
                    boolean commits = matched != null && gitCommitRepository.existsByTaskId(matched.getId());
                    boolean prs = matched != null && gitPullRequestRepository.existsByTaskId(matched.getId());
                    return new GanttTaskResponse(t.getId(),
                            t.getParent() == null ? null : t.getParent().getId(),
                            t.getWbsCode(), t.getTaskName(), t.getTaskType(),
                            t.getPlannedStart(), t.getPlannedFinish(),
                            t.getDurationMinutes(), t.getPlannedEffortMinutes(),
                            t.getPercentComplete(), t.getStatus() == null ? null : t.getStatus().name(),
                            t.getScheduleMode() == null ? null : t.getScheduleMode().name(),
                            critical.getOrDefault(t.getId(), false),
                            baseline.get(t.getId()),
                            resources.getOrDefault(t.getId(), List.of()),
                            commits,
                            prs);
                })
                .toList();

        List<GanttDependencyResponse> dependencyDtos = dependencyRepository.findByPlan_Id(planId).stream()
                .map(d -> new GanttDependencyResponse(d.getPredecessor().getId(), d.getSuccessor().getId(),
                        d.getDependencyType(), d.getLagMinutes()))
                .toList();

        // Fetch sprints of project
        List<GanttDataResponse.GanttSprintResponse> sprintDtos = sprintRepository.findByProjectIdAndDeletedAtIsNull(plan.getProject().getId()).stream()
                .map(s -> new GanttDataResponse.GanttSprintResponse(
                        s.getId(),
                        s.getSprintName(),
                        s.getStartDate(),
                        s.getEndDate(),
                        s.getStatus().name()
                ))
                .toList();

        GanttPlanBrief brief = new GanttPlanBrief(plan.getId(), plan.getPlanCode(), plan.getPlanName(),
                plan.getPlanType() == null ? null : plan.getPlanType().name(),
                plan.getStatus() == null ? null : plan.getStatus().name());

        return new GanttDataResponse(brief, taskDtos, dependencyDtos, sprintDtos, List.of());
    }

    private Map<UUID, Boolean> criticalFlags(UUID planId) {
        Map<UUID, Boolean> flags = new HashMap<>();
        try {
            CriticalPathResult result = criticalPathService.calculate(planId);
            result.tasks().forEach(t -> flags.put(t.taskId(), t.isCritical()));
        } catch (Exception ex) {
            // live tính — nếu plan chưa có ngày lập lịch, bỏ qua cờ critical
        }
        return flags;
    }

    private Map<UUID, GanttBaseline> baselineOverlay(ProjectPlan plan) {
        Map<UUID, GanttBaseline> overlay = new HashMap<>();
        baselineRepository.findFirstByPlanIdAndDeletedAtIsNullOrderByBaselineNumDesc(plan.getId())
                .ifPresent(baseline -> baselineTaskRepository.findByBaselineId(baseline.getId())
                        .stream()
                        .filter(bt -> bt.getTask() != null && bt.getPlannedStart() != null)
                        .forEach(bt -> overlay.put(bt.getTask().getId(),
                                new GanttBaseline(bt.getPlannedStart(), bt.getPlannedFinish()))));
        return overlay;
    }

    private Map<UUID, List<GanttResource>> resourceMap(UUID planId) {
        Map<UUID, List<GanttResource>> byTask = new HashMap<>();
        for (PlanTaskResource r : resourceRepository.findByPlanId(planId)) {
            byTask.computeIfAbsent(r.getTask().getId(), k -> new ArrayList<>())
                    .add(new GanttResource(r.getResourceId(),
                            r.getResourceType() == null ? null : r.getResourceType().name(),
                            r.getAllocationPercent()));
        }
        return byTask;
    }

    private void checkProjectViewAccess(UserPrincipal actor, UUID projectId) {
        if (actor.getRoles().contains("ADMIN")) {
            return;
        }
        if (!memberRepository.existsByProjectIdAndUser_Id(projectId, actor.getId())) {
            throw new AccessDeniedException("Access denied to project");
        }
    }
}