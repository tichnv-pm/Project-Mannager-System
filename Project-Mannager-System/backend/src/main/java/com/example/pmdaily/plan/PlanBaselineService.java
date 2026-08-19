package com.example.pmdaily.plan;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.pmdaily.audit.AuditService;
import com.example.pmdaily.common.ErrorCode;
import com.example.pmdaily.exception.BusinessException;
import com.example.pmdaily.exception.ResourceNotFoundException;
import com.example.pmdaily.plan.dto.BaselineResponse;
import com.example.pmdaily.plan.dto.BaselineVarianceResponse;
import com.example.pmdaily.plan.dto.BaselineVarianceRow;
import com.example.pmdaily.plan.dto.ResourceSnapshotEntry;
import com.example.pmdaily.project.ProjectMember;
import com.example.pmdaily.project.ProjectMemberRepository;
import com.example.pmdaily.project.ProjectMemberRole;
import com.example.pmdaily.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Baseline kế hoạch (docs/planning/11 muc 2-3, docs/api/13-planning-api.md muc 2.5) — PLN-FR-BASE-01..05.
 * Rules: BASE-01 (chỉ APPROVED), BASE-02/05 (bất biến, không ghi đè), BASE-03 (snapshot toàn tree + resource),
 * BASE-04 (variance khi có baseline).
 */
@Service
@Transactional(readOnly = true)
public class PlanBaselineService {

    private static final Logger log = LoggerFactory.getLogger(PlanBaselineService.class);

    private final PlanBaselineRepository baselineRepository;
    private final PlanBaselineTaskRepository baselineTaskRepository;
    private final ProjectPlanRepository planRepository;
    private final PlanTaskRepository taskRepository;
    private final PlanTaskResourceRepository resourceRepository;
    private final ProjectMemberRepository memberRepository;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    public PlanBaselineService(PlanBaselineRepository baselineRepository,
            PlanBaselineTaskRepository baselineTaskRepository,
            ProjectPlanRepository planRepository,
            PlanTaskRepository taskRepository,
            PlanTaskResourceRepository resourceRepository,
            ProjectMemberRepository memberRepository,
            ObjectMapper objectMapper,
            AuditService auditService) {
        this.baselineRepository = baselineRepository;
        this.baselineTaskRepository = baselineTaskRepository;
        this.planRepository = planRepository;
        this.taskRepository = taskRepository;
        this.resourceRepository = resourceRepository;
        this.memberRepository = memberRepository;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    @Transactional
    public BaselineResponse create(UserPrincipal actor, UUID planId, String description) {
        ProjectPlan plan = findPlan(planId);
        checkProjectManageAccess(actor, plan.getProject().getId());
        if (plan.getStatus() != PlanStatus.APPROVED) {
            throw new BusinessException(ErrorCode.PLAN_NOT_APPROVED,
                    "Baseline chỉ tạo được khi kế hoạch ở trạng thái APPROVED");
        }

        int nextNum = baselineRepository.findByPlanIdOrderByBaselineNumDesc(planId).stream()
                .map(PlanBaseline::getBaselineNum)
                .findFirst()
                .map(n -> n + 1)
                .orElse(1);

        PlanBaseline baseline = new PlanBaseline();
        baseline.setPlan(plan);
        baseline.setPlanVersion(plan.getActiveVersion());
        baseline.setBaselineNum(nextNum);
        baseline.setDescription(description);
        baseline.setCapturedAt(Instant.now());
        baseline.setCapturedBy(actor.getId());
        baselineRepository.save(baseline);

        Map<UUID, List<PlanTaskResource>> resourcesByTask = new HashMap<>();
        for (PlanTaskResource r : resourceRepository.findByPlanId(planId)) {
            resourcesByTask.computeIfAbsent(r.getTask().getId(), k -> new ArrayList<>()).add(r);
        }
        for (PlanTask task : taskRepository.findByPlanIdAndDeletedAtIsNull(planId)) {
            PlanBaselineTask row = new PlanBaselineTask();
            row.setBaseline(baseline);
            row.setTask(task);
            row.setWbsCode(task.getWbsCode());
            row.setTaskName(task.getTaskName());
            row.setTaskType(task.getTaskType());
            row.setPlannedStart(task.getPlannedStart());
            row.setPlannedFinish(task.getPlannedFinish());
            row.setDurationMinutes(task.getDurationMinutes() == null ? null : task.getDurationMinutes().intValue());
            row.setPlannedEffortMinutes(task.getPlannedEffortMinutes());
            row.setPercentComplete(task.getPercentComplete());
            row.setResourcesSnapshot(writeResources(resourcesByTask.getOrDefault(task.getId(), List.of())));
            baselineTaskRepository.save(row);
        }
        baselineTaskRepository.flush();

        auditService.record("PLAN_BASELINE_CREATED", "PLAN_BASELINE", baseline.getId(),
                Map.of("planId", String.valueOf(planId), "baselineNum", baseline.getBaselineNum()));

        log.info("plan-baseline.create success plan={} baselineNum={} actor={}",
                planId, nextNum, actor.getUsername());
        return toResponse(baseline);
    }

    public List<BaselineResponse> list(UUID planId) {
        findPlan(planId);
        return baselineRepository.findByPlanIdAndDeletedAtIsNullOrderByBaselineNumDesc(planId).stream()
                .map(this::toResponse)
                .toList();
    }

    public BaselineVarianceResponse variance(UUID planId, int baselineNum) {
        ProjectPlan plan = findPlan(planId);
        PlanBaseline baseline = baselineRepository
                .findByPlanIdAndBaselineNumAndDeletedAtIsNull(planId, baselineNum)
                .orElseThrow(() -> new ResourceNotFoundException("Baseline", baselineNum + ""));

        List<BaselineVarianceRow> rows = new ArrayList<>();
        for (PlanBaselineTask row : baselineTaskRepository.findByBaselineId(baseline.getId())) {
            PlanTask current = row.getTask();
            boolean deleted = current == null || current.getDeletedAt() != null;
            if (!deleted) {
                current = taskRepository.findByIdAndPlanIdAndDeletedAtIsNull(current.getId(), planId)
                        .orElse(null);
                deleted = current == null;
            }
            LocalDate curStart = deleted ? null : current.getPlannedStart();
            LocalDate curFinish = deleted ? null : current.getPlannedFinish();
            Integer curDuration = deleted ? null : (current.getDurationMinutes() == null ? null
                    : current.getDurationMinutes().intValue());
            Integer curEffort = deleted ? null : current.getPlannedEffortMinutes();
            int curProgress = deleted ? 0 : current.getPercentComplete();
            boolean milestoneDone = row.getTaskType() == PlanTaskType.MILESTONE
                    && row.getPercentComplete() < 100 && curProgress >= 100;
            rows.add(new BaselineVarianceRow(
                    deleted ? null : current.getId(),
                    row.getWbsCode(), row.getTaskName(), row.getTaskType(),
                    row.getPlannedStart(), row.getPlannedFinish(),
                    curStart, curFinish,
                    row.getDurationMinutes(), curDuration,
                    row.getPlannedEffortMinutes(), curEffort,
                    row.getPercentComplete(), curProgress,
                    daysBetween(row.getPlannedStart(), curStart),
                    daysBetween(row.getPlannedFinish(), curFinish),
                    diffMinutes(row.getDurationMinutes(), curDuration),
                    diffMinutes(row.getPlannedEffortMinutes(), curEffort),
                    curProgress - row.getPercentComplete(),
                    milestoneDone, deleted));
        }
        return new BaselineVarianceResponse(baseline.getId(), baseline.getBaselineNum(), planId,
                plan.getPlanName(), rows);
    }

    @Transactional
    public void delete(UserPrincipal actor, UUID planId, int baselineNum) {
        findPlan(planId);
        PlanBaseline baseline = baselineRepository
                .findByPlanIdAndBaselineNumAndDeletedAtIsNull(planId, baselineNum)
                .orElseThrow(() -> new ResourceNotFoundException("Baseline", baselineNum + ""));
        checkProjectManageAccess(actor, baseline.getPlan().getProject().getId());

        baseline.setDeletedAt(Instant.now());
        baseline.setDeletedBy(actor.getId());
        baselineRepository.save(baseline);

        auditService.record("PLAN_BASELINE_DELETED", "PLAN_BASELINE", baseline.getId(),
                Map.of("planId", String.valueOf(planId), "baselineNum", baselineNum));

        log.info("plan-baseline.delete success plan={} baselineNum={} actor={}",
                planId, baselineNum, actor.getUsername());
    }

    // ===================== helpers =====================

    private Long daysBetween(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            return null;
        }
        return ChronoUnit.DAYS.between(from, to);
    }

    private Long diffMinutes(Integer from, Integer to) {
        if (from == null || to == null) {
            return null;
        }
        return (long) (to - from);
    }

    private String writeResources(List<PlanTaskResource> rows) {
        try {
            return objectMapper.writeValueAsString(ResourceSnapshotEntry.list(rows));
        } catch (Exception e) {
            throw new IllegalStateException("Không tạo được resources_snapshot", e);
        }
    }

    private BaselineResponse toResponse(PlanBaseline baseline) {
        long count = baselineTaskRepository.findByBaselineId(baseline.getId()).size();
        return new BaselineResponse(baseline.getId(), baseline.getPlan().getId(), baseline.getBaselineNum(),
                baseline.getPlanVersion() == null ? null : baseline.getPlanVersion().getVersionNo(),
                baseline.getDescription(), baseline.getCapturedAt(), baseline.getCapturedBy(), (int) count);
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