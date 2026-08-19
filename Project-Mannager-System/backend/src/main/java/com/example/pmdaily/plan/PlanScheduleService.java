package com.example.pmdaily.plan;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.pmdaily.audit.AuditService;
import com.example.pmdaily.exception.ResourceNotFoundException;
import com.example.pmdaily.plan.dto.RecalcResponse;
import com.example.pmdaily.project.ProjectMember;
import com.example.pmdaily.project.ProjectMemberRepository;
import com.example.pmdaily.project.ProjectMemberRole;
import com.example.pmdaily.security.UserPrincipal;

/**
 * Nghiệp vụ scheduling (docs/api/13-planning-api.md muc 2.1 — POST /plans/{id}/recalc, muc 2.6).
 * Phân quyền: plan:schedule (ADMIN, PM dự án — docs/planning/04).
 */
@Service
@Transactional(readOnly = true)
public class PlanScheduleService {

    private static final Logger log = LoggerFactory.getLogger(PlanScheduleService.class);

    private final ProjectPlanRepository planRepository;
    private final ProjectMemberRepository memberRepository;
    private final SchedulingEngine engine;
    private final AuditService auditService;

    public PlanScheduleService(
            ProjectPlanRepository planRepository,
            ProjectMemberRepository memberRepository,
            SchedulingEngine engine,
            AuditService auditService) {
        this.planRepository = planRepository;
        this.memberRepository = memberRepository;
        this.engine = engine;
        this.auditService = auditService;
    }

    @Transactional
    public RecalcResponse recalculate(UserPrincipal actor, UUID planId) {
        ProjectPlan plan = planRepository.findByIdAndDeletedAtIsNull(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Kế hoạch", planId));
        checkProjectManageAccess(actor, plan.getProject().getId());

        RecalcResponse result = engine.recalculate(planId);

        auditService.record("PLAN_RECALCULATED", "PLAN", planId,
                Map.of("scheduledTasks", String.valueOf(result.scheduledTasks()),
                        "warnings", String.valueOf(result.warnings().size())));

        log.info("plan.schedule.recalc success planId={} actor={}",
                planId, actor.getUsername());
        return result;
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