package com.example.pmdaily.plan;

import java.util.List;
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
import com.example.pmdaily.issue.Issue;
import com.example.pmdaily.issue.IssueRepository;
import com.example.pmdaily.milestone.Milestone;
import com.example.pmdaily.milestone.MilestoneRepository;
import com.example.pmdaily.plan.dto.LinkCreateRequest;
import com.example.pmdaily.plan.dto.LinkResponse;
import com.example.pmdaily.project.Project;
import com.example.pmdaily.project.ProjectMember;
import com.example.pmdaily.project.ProjectMemberRepository;
import com.example.pmdaily.project.ProjectMemberRole;
import com.example.pmdaily.risk.Risk;
import com.example.pmdaily.risk.RiskRepository;
import com.example.pmdaily.security.UserPrincipal;
import com.example.pmdaily.task.Task;
import com.example.pmdaily.task.TaskRepository;

/**
 * Liên kết planning task ↔ entity ngoài (docs/api/13-planning-api.md muc 2.8, docs/planning/02 muc 2.11).
 * Rules: LINK-01 (bảng plan_links), LINK-02 (1 Execution Task tối đa 1 planning task chính),
 * LINK-06 (BLOCKED_BY chỉ Issue/Risk), AC-LINK-03 (target phải tồn tại và cùng project).
 */
@Service
@Transactional(readOnly = true)
public class PlanLinkService {

    private static final Logger log = LoggerFactory.getLogger(PlanLinkService.class);

    private final PlanLinkRepository linkRepository;
    private final ProjectPlanRepository planRepository;
    private final PlanTaskRepository taskRepository;
    private final TaskRepository executionTaskRepository;
    private final IssueRepository issueRepository;
    private final RiskRepository riskRepository;
    private final MilestoneRepository milestoneRepository;
    private final ProjectMemberRepository memberRepository;
    private final AuditService auditService;
    private final PlanChangeHistoryService changeHistoryService;

    public PlanLinkService(PlanLinkRepository linkRepository,
            ProjectPlanRepository planRepository,
            PlanTaskRepository taskRepository,
            TaskRepository executionTaskRepository,
            IssueRepository issueRepository,
            RiskRepository riskRepository,
            MilestoneRepository milestoneRepository,
            ProjectMemberRepository memberRepository,
            AuditService auditService,
            PlanChangeHistoryService changeHistoryService) {
        this.linkRepository = linkRepository;
        this.planRepository = planRepository;
        this.taskRepository = taskRepository;
        this.executionTaskRepository = executionTaskRepository;
        this.issueRepository = issueRepository;
        this.riskRepository = riskRepository;
        this.milestoneRepository = milestoneRepository;
        this.memberRepository = memberRepository;
        this.auditService = auditService;
        this.changeHistoryService = changeHistoryService;
    }

    @Transactional
    public LinkResponse create(UserPrincipal actor, UUID planId, UUID taskId, LinkCreateRequest request) {
        ProjectPlan plan = findPlan(planId);
        checkProjectManageAccess(actor, plan.getProject().getId());
        PlanTask task = taskRepository.findByIdAndPlanIdAndDeletedAtIsNull(taskId, planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan task", taskId));

        PlanLinkTargetType targetType = parseTargetType(request.targetType());
        PlanLinkType linkType = parseLinkType(request.linkType());
        boolean primary = Boolean.TRUE.equals(request.isPrimaryExecution());

        if (linkType == PlanLinkType.BLOCKED_BY
                && targetType != PlanLinkTargetType.ISSUE && targetType != PlanLinkTargetType.RISK) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "linkType BLOCKED_BY chỉ áp dụng cho ISSUE hoặc RISK");
        }
        if (primary && targetType != PlanLinkTargetType.EXECUTION_TASK) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "isPrimaryExecution chỉ áp dụng với EXECUTION_TASK");
        }
        validateTargetExists(targetType, request.targetId(), plan);

        if (linkRepository.existsByPlanningTaskIdAndTargetTypeAndTargetIdAndDeletedAtIsNull(
                taskId, targetType, request.targetId())) {
            throw new ConflictException("Đối tượng đã được liên kết với task này");
        }
        if (primary) {
            linkRepository.findByPlanningTaskIdAndIsPrimaryExecutionTrueAndDeletedAtIsNull(taskId)
                    .ifPresent(l -> {
                        throw new ConflictException("Task đã có execution task chính");
                    });
            linkRepository.findByTargetTypeAndTargetIdAndIsPrimaryExecutionTrueAndDeletedAtIsNull(
                            PlanLinkTargetType.EXECUTION_TASK, request.targetId())
                    .ifPresent(l -> {
                        throw new BusinessException(ErrorCode.ALREADY_LINKED,
                                "Execution task đã là chính của một planning task khác");
                    });
        }

        PlanLink link = new PlanLink();
        link.setPlan(plan);
        link.setPlanningTask(task);
        link.setTargetType(targetType);
        link.setTargetId(request.targetId());
        link.setLinkType(linkType);
        link.setNote(request.note());
        link.setPrimaryExecution(primary);
        linkRepository.saveAndFlush(link);

        auditService.record("PLAN_LINK_CREATED", "PLAN_LINK", link.getId(),
                java.util.Map.of("planId", String.valueOf(planId), "taskId", String.valueOf(taskId),
                        "targetType", targetType.name(), "targetId", String.valueOf(request.targetId()),
                        "linkType", linkType.name(), "isPrimary", primary));
        changeHistoryService.record(actor, plan, "PLAN_LINK_ADDED", "PLAN_LINK", link.getId(),
                "linkType", targetType.name() + "/" + request.targetId(), linkType.name(), request.note());

        log.info("plan-link.create success id={} task={} target={}/{} actor={}", link.getId(), taskId,
                targetType, request.targetId(), actor.getUsername());
        return toResponse(link);
    }

    @Transactional(readOnly = true)
    public List<LinkResponse> list(UUID planId, UUID taskId) {
        taskRepository.findByIdAndPlanIdAndDeletedAtIsNull(taskId, planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan task", taskId));
        return linkRepository.findByPlanningTaskIdAndDeletedAtIsNullOrderByCreatedAtAsc(taskId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public void delete(UserPrincipal actor, UUID linkId) {
        PlanLink link = linkRepository.findByIdAndDeletedAtIsNull(linkId)
                .orElseThrow(() -> new ResourceNotFoundException("Link", linkId));
        checkProjectManageAccess(actor, link.getPlan().getProject().getId());

        link.setDeletedAt(java.time.Instant.now());
        link.setDeletedBy(actor.getId());
        linkRepository.saveAndFlush(link);

        auditService.record("PLAN_LINK_DELETED", "PLAN_LINK", link.getId(),
                java.util.Map.of("planId", String.valueOf(link.getPlan().getId()),
                        "targetType", link.getTargetType().name(),
                        "targetId", String.valueOf(link.getTargetId())));
        changeHistoryService.record(actor, link.getPlan(), "PLAN_LINK_REMOVED", "PLAN_LINK", link.getId(),
                "target", link.getTargetType().name() + "/" + link.getTargetId(), null, null);

        log.info("plan-link.delete success id={} actor={}", linkId, actor.getUsername());
    }

    private void validateTargetExists(PlanLinkTargetType targetType, UUID targetId, ProjectPlan plan) {
        UUID projectId = plan.getProject().getId();
        switch (targetType) {
            case EXECUTION_TASK -> {
                Task task = executionTaskRepository.findById(targetId)
                        .orElseThrow(() -> new ResourceNotFoundException("Execution task", targetId));
                requireSameProject(projectId, task.getProject(), "Execution task");
            }
            case ISSUE -> {
                Issue issue = issueRepository.findById(targetId)
                        .orElseThrow(() -> new ResourceNotFoundException("Issue", targetId));
                requireSameProject(projectId, issue.getProject(), "Issue");
            }
            case RISK -> {
                Risk risk = riskRepository.findById(targetId)
                        .orElseThrow(() -> new ResourceNotFoundException("Risk", targetId));
                requireSameProject(projectId, risk.getProject(), "Risk");
            }
            case MILESTONE -> {
                Milestone milestone = milestoneRepository.findById(targetId)
                        .orElseThrow(() -> new ResourceNotFoundException("Milestone", targetId));
                requireSameProject(projectId, milestone.getProject(), "Milestone");
            }
        }
    }

    private void requireSameProject(UUID projectId, Project targetProject, String label) {
        if (!projectId.equals(targetProject.getId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    label + " phải thuộc cùng project với kế hoạch");
        }
    }

    private PlanLinkTargetType parseTargetType(String value) {
        try {
            return PlanLinkTargetType.valueOf(value.toUpperCase());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "targetType không hợp lệ: " + value);
        }
    }

    private PlanLinkType parseLinkType(String value) {
        try {
            return PlanLinkType.valueOf(value.toUpperCase());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "linkType không hợp lệ: " + value);
        }
    }

    private LinkResponse toResponse(PlanLink link) {
        return new LinkResponse(link.getId(), link.getPlan().getId(), link.getPlanningTask().getId(),
                link.getTargetType().name(), link.getTargetId(), link.getLinkType().name(),
                link.getNote(), link.isPrimaryExecution(), link.getCreatedBy(), link.getCreatedAt());
    }

    private ProjectPlan findPlan(UUID planId) {
        return planRepository.findByIdAndDeletedAtIsNull(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Kế hoạch", planId));
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
}