package com.example.pmdaily.plan;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.pmdaily.audit.AuditService;
import com.example.pmdaily.common.ErrorCode;
import com.example.pmdaily.exception.BusinessException;
import com.example.pmdaily.exception.ConflictException;
import com.example.pmdaily.exception.ResourceNotFoundException;
import com.example.pmdaily.plan.dto.DependencyCreateRequest;
import com.example.pmdaily.plan.dto.DependencyResponse;
import com.example.pmdaily.plan.mapper.PlanDependencyMapper;
import com.example.pmdaily.project.ProjectMember;
import com.example.pmdaily.project.ProjectMemberRepository;
import com.example.pmdaily.project.ProjectMemberRole;
import com.example.pmdaily.security.UserPrincipal;

/**
 * Nghiệp vụ dependency giữa planning task (docs/api/13-planning-api.md muc 2.3, docs/planning/08) — PLN-FR-DEP-01..06.
 * Rules: PLN-RULE-DEP-01 (no self), DEP-02 (cấm cycle), DEP-03 (cùng plan), DEP-04 (lag âm được phép),
 * DEP-05 (unique cặp pred/succ/type).
 */
@Service
@Transactional(readOnly = true)
public class PlanTaskDependencyService {

    private static final Logger log = LoggerFactory.getLogger(PlanTaskDependencyService.class);

    private final PlanTaskDependencyRepository dependencyRepository;
    private final PlanTaskRepository taskRepository;
    private final ProjectPlanRepository planRepository;
    private final ProjectMemberRepository memberRepository;
    private final PlanDependencyMapper dependencyMapper;
    private final AuditService auditService;
    private final PlanChangeHistoryService changeHistoryService;

    public PlanTaskDependencyService(
            PlanTaskDependencyRepository dependencyRepository,
            PlanTaskRepository taskRepository,
            ProjectPlanRepository planRepository,
            ProjectMemberRepository memberRepository,
            PlanDependencyMapper dependencyMapper,
            AuditService auditService,
            PlanChangeHistoryService changeHistoryService) {
        this.dependencyRepository = dependencyRepository;
        this.taskRepository = taskRepository;
        this.planRepository = planRepository;
        this.memberRepository = memberRepository;
        this.dependencyMapper = dependencyMapper;
        this.auditService = auditService;
        this.changeHistoryService = changeHistoryService;
    }

    @Transactional
    public DependencyResponse create(UserPrincipal actor, UUID planId, UUID successorTaskId,
            DependencyCreateRequest request) {
        ProjectPlan plan = findPlan(planId);
        checkProjectManageAccess(actor, plan.getProject().getId());

        PlanTask successor = findTask(planId, successorTaskId);

        if (request.predecessorTaskId().equals(successorTaskId)) {
            throw new BusinessException(ErrorCode.SELF_DEPENDENCY);
        }

        PlanTask predecessor = taskRepository.findByIdAndPlanIdAndDeletedAtIsNull(
                        request.predecessorTaskId(), planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CROSS_PROJECT_DEPENDENCY));

        if (dependencyRepository.existsByPlanIdAndPredecessorIdAndSuccessorIdAndDependencyType(
                planId, predecessor.getId(), successor.getId(), request.dependencyType())) {
            throw new ConflictException("Dependency đã tồn tại (predecessor, successor, type)");
        }

        if (createsCycle(planId, predecessor.getId(), successor.getId())) {
            throw new BusinessException(ErrorCode.DEPENDENCY_CYCLE);
        }

        PlanTaskDependency dependency = new PlanTaskDependency();
        dependency.setPlan(plan);
        dependency.setPredecessor(predecessor);
        dependency.setSuccessor(successor);
        dependency.setDependencyType(request.dependencyType());
        dependency.setLagMinutes(request.lag());

        dependencyRepository.saveAndFlush(dependency);

        changeHistoryService.record(actor, plan, "PLAN_DEPENDENCY_ADDED", "PLAN_DEPENDENCY",
                dependency.getId(), "dependency", predecessor.getTaskCode() + " -> "
                        + successor.getTaskCode() + " [" + request.dependencyType() + "]",
                null, null);

        auditService.record("PLAN_DEPENDENCY_CREATED", "PLAN_DEPENDENCY", dependency.getId(),
                Map.of("predecessor", predecessor.getTaskCode(), "successor", successor.getTaskCode(),
                        "type", request.dependencyType(), "lagMinutes", dependency.getLagMinutes()));

        log.info("plan-dependency.create success id={} pred={} succ={} actor={}",
                dependency.getId(), predecessor.getTaskCode(), successor.getTaskCode(), actor.getUsername());
        return dependencyMapper.toResponse(dependency);
    }

    @Transactional
    public void delete(UserPrincipal actor, UUID planId, UUID successorTaskId, UUID dependencyId) {
        ProjectPlan plan = findPlan(planId);
        checkProjectManageAccess(actor, plan.getProject().getId());
        findTask(planId, successorTaskId);

        PlanTaskDependency dependency = dependencyRepository.findByIdAndPlanId(dependencyId, planId)
                .orElseThrow(() -> new ResourceNotFoundException("Dependency", dependencyId));
        if (!dependency.getSuccessor().getId().equals(successorTaskId)) {
            throw new ResourceNotFoundException("Dependency", dependencyId);
        }

        dependencyRepository.delete(dependency);
        dependencyRepository.flush();

        changeHistoryService.record(actor, plan, "PLAN_DEPENDENCY_REMOVED", "PLAN_DEPENDENCY",
                dependency.getId(), "dependency",
                dependency.getPredecessor().getTaskCode() + " -> "
                        + dependency.getSuccessor().getTaskCode() + " [" + dependency.getDependencyType() + "]",
                null, null);

        auditService.record("PLAN_DEPENDENCY_DELETED", "PLAN_DEPENDENCY", dependency.getId(),
                Map.of("predecessor", dependency.getPredecessor().getTaskCode(),
                        "successor", dependency.getSuccessor().getTaskCode(),
                        "type", dependency.getDependencyType()));

        log.info("plan-dependency.delete success id={} actor={}", dependencyId, actor.getUsername());
    }

    /**
     * Danh sách dependency của plan (cho UI Dependency Editor — bổ sung GET read-only
     * cho docs/api/13-planning-api.md mục 2.3, PLN-FR-DEP-01).
     */
    public List<DependencyResponse> list(UUID planId, UserPrincipal actor) {
        ProjectPlan plan = findPlan(planId);
        checkProjectViewAccess(actor, plan.getProject().getId());
        return dependencyRepository.findByPlan_Id(planId).stream()
                .sorted(Comparator.comparing(d -> d.getSuccessor().getTaskCode()))
                .map(dependencyMapper::toResponse)
                .toList();
    }

    // ===================== helpers =====================

    /**
     * Kiểm tra thêm cạnh pred→succ có tạo chu trình trong đồ thị dependency hiện tại (PLN-RULE-DEP-02).
     * Đồ thị: mỗi dependency là cạnh predecessor → successor; có chu trình nếu succ "đi được" tới pred.
     */
    private boolean createsCycle(UUID planId, UUID predecessorId, UUID successorId) {
        Map<UUID, List<UUID>> graph = new HashMap<>();
        for (PlanTaskDependency d : dependencyRepository.findByPlan_Id(planId)) {
            graph.computeIfAbsent(d.getPredecessor().getId(), k -> new ArrayList<>())
                    .add(d.getSuccessor().getId());
        }
        Set<UUID> visited = new HashSet<>();
        Deque<UUID> stack = new ArrayDeque<>();
        stack.push(successorId);
        while (!stack.isEmpty()) {
            UUID node = stack.pop();
            if (node.equals(predecessorId)) {
                return true;
            }
            if (!visited.add(node)) {
                continue;
            }
            for (UUID next : graph.getOrDefault(node, List.of())) {
                stack.push(next);
            }
        }
        return false;
    }

    private ProjectPlan findPlan(UUID planId) {
        return planRepository.findByIdAndDeletedAtIsNull(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Kế hoạch", planId));
    }

    private PlanTask findTask(UUID planId, UUID taskId) {
        return taskRepository.findByIdAndPlanIdAndDeletedAtIsNull(taskId, planId)
                .orElseThrow(() -> new ResourceNotFoundException("Planning task", taskId));
    }

    private void checkProjectManageAccess(UserPrincipal actor, UUID projectId) {
        if (actor.getRoles().contains("ADMIN")) {
            return;
        }
        ProjectMember member = memberRepository.findByProjectIdAndUser_Id(projectId, actor.getId())
                .orElseThrow(() -> new AccessDeniedException("Access denied to project"));
        if (member.getRole() != ProjectMemberRole.PROJECT_MANAGER) {
            throw new AccessDeniedException("Cần quyền PROJECT_MANAGER của dự án");
        }
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