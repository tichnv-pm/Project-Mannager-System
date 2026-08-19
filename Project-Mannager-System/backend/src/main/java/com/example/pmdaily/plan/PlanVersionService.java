package com.example.pmdaily.plan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.pmdaily.audit.AuditService;
import com.example.pmdaily.exception.BusinessException;
import com.example.pmdaily.exception.ResourceNotFoundException;
import com.example.pmdaily.plan.dto.TaskDiffResponse;
import com.example.pmdaily.plan.dto.VersionDiffResponse;
import com.example.pmdaily.plan.dto.VersionResponse;
import com.example.pmdaily.project.ProjectMember;
import com.example.pmdaily.project.ProjectMemberRepository;
import com.example.pmdaily.project.ProjectMemberRole;
import com.example.pmdaily.security.UserPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Phiên bản kế hoạch (docs/planning/11 muc 1, docs/api/13-planning-api.md muc 2.5) — PLN-FR-VERSION-01..05.
 * Rules: VERSION-01 (versionNo tăng đơn điệu), VERSION-02 (1 ACTIVE), VERSION-03 (snapshot bất biến).
 */
@Service
@Transactional(readOnly = true)
public class PlanVersionService {

    private static final Logger log = LoggerFactory.getLogger(PlanVersionService.class);
    private static final String[] DIFF_FIELDS =
            {"plannedStart", "plannedFinish", "durationMinutes", "plannedEffortMinutes", "percentComplete"};

    private final PlanVersionRepository versionRepository;
    private final ProjectPlanRepository planRepository;
    private final PlanTaskRepository taskRepository;
    private final PlanTaskDependencyRepository dependencyRepository;
    private final PlanTaskResourceRepository resourceRepository;
    private final ProjectMemberRepository memberRepository;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    public PlanVersionService(PlanVersionRepository versionRepository,
            ProjectPlanRepository planRepository,
            PlanTaskRepository taskRepository,
            PlanTaskDependencyRepository dependencyRepository,
            PlanTaskResourceRepository resourceRepository,
            ProjectMemberRepository memberRepository,
            ObjectMapper objectMapper,
            AuditService auditService) {
        this.versionRepository = versionRepository;
        this.planRepository = planRepository;
        this.taskRepository = taskRepository;
        this.dependencyRepository = dependencyRepository;
        this.resourceRepository = resourceRepository;
        this.memberRepository = memberRepository;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    @Transactional
    public VersionResponse create(UserPrincipal actor, UUID planId, String note) {
        ProjectPlan plan = findPlan(planId);
        checkProjectManageAccess(actor, plan.getProject().getId());

        int nextNo = versionRepository.findFirstByPlanIdOrderByVersionNoDesc(planId)
                .map(PlanVersion::getVersionNo).orElse(0) + 1;

        PlanVersion version = new PlanVersion();
        version.setPlan(plan);
        version.setVersionNo(nextNo);
        version.setStatus(PlanVersionStatus.ACTIVE);
        version.setNote(note);
        version.setSnapshotJson(buildSnapshot(plan));
        versionRepository.save(version);

        for (PlanVersion old : versionRepository.findByPlanIdOrderByVersionNoDesc(planId)) {
            if (old.getStatus() == PlanVersionStatus.ACTIVE && !old.getId().equals(version.getId())) {
                old.setStatus(PlanVersionStatus.INACTIVE);
                versionRepository.save(old);
            }
        }
        plan.setActiveVersion(version);
        planRepository.saveAndFlush(plan);

        auditService.record("PLAN_VERSION_CREATED", "PLAN_VERSION", version.getId(),
                Map.of("planId", String.valueOf(planId), "versionNo", version.getVersionNo()));

        log.info("plan-version.create success plan={} versionNo={} actor={}",
                planId, nextNo, actor.getUsername());
        return toResponse(planId, version, plan);
    }

    public List<VersionResponse> list(UUID planId) {
        ProjectPlan plan = findPlan(planId);
        return versionRepository.findByPlanIdOrderByVersionNoDesc(planId).stream()
                .map(v -> toResponse(planId, v, plan))
                .toList();
    }

    /** Diff versionNo vs versionNo+1 (docs/planning/11 muc 1, PLN-AC-VERSION-03). */
    public VersionDiffResponse diff(UUID planId, int versionNo) {
        findPlan(planId);
        PlanVersion from = findVersion(planId, versionNo);
        PlanVersion to = versionRepository.findByPlanIdOrderByVersionNoDesc(planId).stream()
                .filter(v -> v.getVersionNo() == versionNo + 1)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Phiên bản mới hơn", UUID.randomUUID()));

        List<TaskDiffResponse> diffs = new ArrayList<>();
        Map<String, JsonNode> fromTasks = snapshotTasks(from);
        Map<String, JsonNode> toTasks = snapshotTasks(to);
        for (Map.Entry<String, JsonNode> entry : toTasks.entrySet()) {
            String wbs = entry.getKey();
            JsonNode toTask = entry.getValue();
            JsonNode fromTask = fromTasks.get(wbs);
            if (fromTask == null) {
                diffs.add(new TaskDiffResponse(wbs, toTask.path("taskName").asText(), "TASK_ADDED", null,
                        toTask.path("taskCode").asText()));
                continue;
            }
            for (String field : DIFF_FIELDS) {
                JsonNode a = fromTask.get(field);
                JsonNode b = toTask.get(field);
                if (!jsonEqual(a, b)) {
                    diffs.add(new TaskDiffResponse(wbs, toTask.path("taskName").asText(), field,
                            a == null ? null : a.asText(), b == null ? null : b.asText()));
                }
            }
        }
        for (String wbs : fromTasks.keySet()) {
            if (!toTasks.containsKey(wbs)) {
                JsonNode removed = fromTasks.get(wbs);
                diffs.add(new TaskDiffResponse(wbs, removed.path("taskName").asText(), "TASK_REMOVED",
                        removed.path("taskCode").asText(), null));
            }
        }
        return new VersionDiffResponse(versionNo, to.getVersionNo(), diffs);
    }

    private boolean jsonEqual(JsonNode a, JsonNode b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.equals(b);
    }

    private Map<String, JsonNode> snapshotTasks(PlanVersion version) {
        Map<String, JsonNode> map = new LinkedHashMap<>();
        try {
            JsonNode root = objectMapper.readTree(version.getSnapshotJson());
            for (JsonNode task : root.path("tasks")) {
                map.put(task.path("wbsCode").asText(), task);
            }
        } catch (Exception e) {
            log.warn("plan-version snapshot parse failed id={}", version.getId(), e);
        }
        return map;
    }

    private String buildSnapshot(ProjectPlan plan) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode planNode = root.putObject("plan");
        planNode.put("status", plan.getStatus().name());
        planNode.put("progress", plan.getProgress());
        if (plan.getPlannedStart() != null) {
            planNode.put("plannedStart", plan.getPlannedStart().toString());
        }
        if (plan.getPlannedFinish() != null) {
            planNode.put("plannedFinish", plan.getPlannedFinish().toString());
        }
        if (plan.getDurationMinutes() != null) {
            planNode.put("durationMinutes", plan.getDurationMinutes());
        }

        ArrayNode tasks = root.putArray("tasks");
        for (PlanTask task : taskRepository.findByPlanIdAndDeletedAtIsNull(plan.getId())) {
            ObjectNode t = tasks.addObject();
            t.put("wbsCode", task.getWbsCode());
            t.put("taskCode", task.getTaskCode());
            t.put("taskName", task.getTaskName());
            t.put("taskType", task.getTaskType().name());
            t.put("plannedStart", task.getPlannedStart() == null ? null : task.getPlannedStart().toString());
            t.put("plannedFinish", task.getPlannedFinish() == null ? null : task.getPlannedFinish().toString());
            t.put("durationMinutes", task.getDurationMinutes());
            t.put("plannedEffortMinutes", task.getPlannedEffortMinutes());
            t.put("percentComplete", task.getPercentComplete());
            t.put("isSummary", task.isSummary());
            t.put("isMilestone", task.isMilestone());
            t.put("scheduleMode", task.getScheduleMode().name());
            t.put("status", task.getStatus().name());
        }

        ArrayNode deps = root.putArray("dependencies");
        for (PlanTaskDependency d : dependencyRepository.findByPlan_Id(plan.getId())) {
            ObjectNode n = deps.addObject();
            n.put("predecessorCode", d.getPredecessor().getTaskCode());
            n.put("successorCode", d.getSuccessor().getTaskCode());
            n.put("dependencyType", d.getDependencyType().name());
            n.put("lagMinutes", d.getLagMinutes());
        }

        ArrayNode resources = root.putArray("resources");
        for (PlanTaskResource r : resourceRepository.findByPlanId(plan.getId())) {
            ObjectNode n = resources.addObject();
            n.put("wbsCode", r.getTask().getWbsCode());
            n.put("resourceType", r.getResourceType().name());
            n.put("resourceId", r.getResourceId().toString());
            n.put("roleOnTask", r.getRoleOnTask());
            n.put("allocationPercent", r.getAllocationPercent());
        }

        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Không tạo được snapshot version", e);
        }
    }

    private VersionResponse toResponse(UUID planId, PlanVersion version, ProjectPlan plan) {
        boolean isActive = plan.getActiveVersion() != null
                && plan.getActiveVersion().getId().equals(version.getId());
        long taskCount = taskRepository.countByPlanIdAndDeletedAtIsNull(planId);
        long depCount = dependencyRepository.findByPlan_Id(planId).size();
        long resCount = resourceRepository.findByPlanId(planId).size();
        return new VersionResponse(version.getId(), planId, version.getVersionNo(),
                version.getStatus().name(), version.getNote(), version.getCreatedAt(),
                (int) taskCount, (int) depCount, (int) resCount, isActive);
    }

    private PlanVersion findVersion(UUID planId, int versionNo) {
        return versionRepository.findByPlanIdOrderByVersionNoDesc(planId).stream()
                .filter(v -> v.getVersionNo() == versionNo)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Phiên bản", versionNo + ""));
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