package com.example.pmdaily.plan;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import com.example.pmdaily.task.TimeUnit;

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
import com.example.pmdaily.plan.dto.PlanTaskCreateRequest;
import com.example.pmdaily.plan.dto.PlanTaskMoveRequest;
import com.example.pmdaily.plan.dto.PlanTaskResponse;
import com.example.pmdaily.plan.dto.PlanTaskUpdateRequest;
import com.example.pmdaily.plan.mapper.PlanTaskMapper;
import com.example.pmdaily.project.ProjectMember;
import com.example.pmdaily.project.ProjectMemberRepository;
import com.example.pmdaily.project.ProjectMemberRole;
import com.example.pmdaily.security.UserPrincipal;
import com.example.pmdaily.user.User;
import com.example.pmdaily.user.UserRepository;

/**
 * Nghiệp vụ WBS / Planning Task (docs/api/13-planning-api.md muc 2.2, docs/planning/07) — PLN-FR-WBS-*.
 * Sau mỗi thao tác cây: renumber wbsCode/sequence/outline + roll-up summary + cập nhật plan.
 */
@Service
@Transactional(readOnly = true)
public class PlanTaskService {

    private static final Logger log = LoggerFactory.getLogger(PlanTaskService.class);

    private final PlanTaskRepository taskRepository;
    private final PlanTaskDependencyRepository dependencyRepository;
    private final ProjectPlanRepository planRepository;
    private final ProjectMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final PlanTaskMapper taskMapper;
    private final AuditService auditService;
    private final PlanChangeHistoryService changeHistoryService;

    public PlanTaskService(
            PlanTaskRepository taskRepository,
            PlanTaskDependencyRepository dependencyRepository,
            ProjectPlanRepository planRepository,
            ProjectMemberRepository memberRepository,
            UserRepository userRepository,
            PlanTaskMapper taskMapper,
            AuditService auditService,
            PlanChangeHistoryService changeHistoryService) {
        this.taskRepository = taskRepository;
        this.dependencyRepository = dependencyRepository;
        this.planRepository = planRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.taskMapper = taskMapper;
        this.auditService = auditService;
        this.changeHistoryService = changeHistoryService;
    }

    public List<PlanTaskResponse> tree(UUID planId, UserPrincipal actor) {
        ProjectPlan plan = findPlan(planId);
        checkProjectViewAccess(actor, plan.getProject().getId());
        return taskRepository.findByPlanIdAndDeletedAtIsNull(planId).stream()
                .sorted(wbsComparator())
                .map(taskMapper::toResponse)
                .toList();
    }

    @Transactional
    public PlanTaskResponse create(UserPrincipal actor, UUID planId, PlanTaskCreateRequest request) {
        ProjectPlan plan = findPlan(planId);
        checkProjectManageAccess(actor, plan.getProject().getId());

        if (taskRepository.existsByPlanIdAndTaskCodeAndDeletedAtIsNull(planId, request.taskCode().trim())) {
            throw new ConflictException("taskCode đã tồn tại trong plan");
        }

        PlanTask parent = null;
        if (request.parentId() != null) {
            parent = findTask(planId, request.parentId());
            if (!canBeParent(parent)) {
                throw new BusinessException(ErrorCode.INVALID_PARENT);
            }
        }

        PlanTask task = new PlanTask();
        task.setPlan(plan);
        task.setParent(parent);
        task.setTaskCode(request.taskCode().trim());
        task.setTaskName(request.taskName().trim());
        task.setDescription(request.description());
        task.setTaskType(request.taskType());
        task.setPhase(request.phase());
        task.setWorkPackage(request.workPackage());
        task.setDeliverable(request.deliverable());
        task.setOwner(resolveOwner(request.ownerId()));
        task.setPlannedStart(request.plannedStart());
        task.setPlannedFinish(request.plannedFinish());
        task.setDurationMinutes(request.durationMinutes());
        task.setDurationUnit(request.durationUnit() != null ? request.durationUnit() : TimeUnit.MINUTE);
        task.setPlannedEffortMinutes(request.plannedEffortMinutes());
        task.setEffortUnit(request.effortUnit() != null ? request.effortUnit() : TimeUnit.MINUTE);
        task.setActualStart(null);
        task.setActualFinish(null);
        task.setActualEffortMinutes(null);
        task.setRemainingEffortMinutes(null);
        task.setPercentComplete(request.percentComplete() == null ? 0 : request.percentComplete());
        task.setStatus(request.status() == null ? PlanTaskStatus.NOT_STARTED : request.status());
        task.setPriority(request.priority());
        task.setScheduleMode(request.scheduleMode() == null ? ScheduleMode.AUTO : request.scheduleMode());
        task.setConstraintType(request.constraintType());
        task.setConstraintDate(request.constraintDate());

        validateDateRange(task);
        applyMilestoneRules(task);
        assignInitialPosition(task);

        taskRepository.saveAndFlush(task);

        renumberAndRollup(plan);

        auditService.record("PLAN_TASK_CREATED", "PLAN_TASK", task.getId(),
                Map.of("taskCode", task.getTaskCode(), "wbsCode", task.getWbsCode(), "planId", planId));

        log.info("plan-task.create success id={} wbs={} actor={}",
                task.getId(), task.getWbsCode(), actor.getUsername());
        return taskMapper.toResponse(task);
    }

    @Transactional
    public PlanTaskResponse update(UserPrincipal actor, UUID planId, UUID taskId, PlanTaskUpdateRequest request) {
        ProjectPlan plan = findPlan(planId);
        checkProjectManageAccess(actor, plan.getProject().getId());
        PlanTask task = findTask(planId, taskId);

        if (!Objects.equals(task.getVersion(), request.version())) {
            throw new ConflictException("Record modified by another transaction");
        }

        String oldName = task.getTaskName();
        LocalDate oldStart = task.getPlannedStart();
        LocalDate oldFinish = task.getPlannedFinish();
        Long oldDuration = task.getDurationMinutes();
        Integer oldEffort = task.getPlannedEffortMinutes();
        int oldPercent = task.getPercentComplete();
        PlanTaskStatus oldStatus = task.getStatus();
        ScheduleMode oldSchedule = task.getScheduleMode();

        task.setTaskName(request.taskName().trim());
        task.setDescription(request.description());
        if (request.ownerId() != null) {
            task.setOwner(resolveOwner(request.ownerId()));
        }
        task.setPlannedStart(request.plannedStart());
        task.setPlannedFinish(request.plannedFinish());
        task.setDurationMinutes(request.durationMinutes());
        task.setDurationUnit(request.durationUnit() != null ? request.durationUnit() : task.getDurationUnit());
        task.setPlannedEffortMinutes(request.plannedEffortMinutes());
        task.setEffortUnit(request.effortUnit() != null ? request.effortUnit() : task.getEffortUnit());
        task.setActualStart(request.actualStart());
        task.setActualFinish(request.actualFinish());
        task.setActualEffortMinutes(request.actualEffortMinutes());
        task.setRemainingEffortMinutes(request.remainingEffortMinutes());
        task.setStatus(request.status() == null ? task.getStatus() : request.status());
        task.setPriority(request.priority());
        task.setScheduleMode(request.scheduleMode() == null ? task.getScheduleMode() : request.scheduleMode());
        task.setConstraintType(request.constraintType());
        task.setConstraintDate(request.constraintDate());
        task.setPhase(request.phase());
        task.setWorkPackage(request.workPackage());
        task.setDeliverable(request.deliverable());

        if (request.taskType() != null && request.taskType() != task.getTaskType()) {
            if (task.getTaskType().isLeaf() != request.taskType().isLeaf()
                    && taskRepository.countByParentIdAndDeletedAtIsNull(task.getId()) > 0
                    && request.taskType().isLeaf()) {
                throw new BusinessException(ErrorCode.INVALID_PARENT,
                        "Task còn con không thể đổi sang loại lá");
            }
            task.setTaskType(request.taskType());
        }

        if (request.percentComplete() != null) {
            if (task.isSummary()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "percentComplete của summary chỉ tính theo roll-up, không sửa tay");
            }
            task.setPercentComplete(request.percentComplete());
        }

        validateDateRange(task);
        applyMilestoneRules(task);

        taskRepository.saveAndFlush(task);
        renumberAndRollup(plan);

        recordIfChanged(actor, plan, task.getId(), "taskName", oldName, task.getTaskName());
        recordIfChanged(actor, plan, task.getId(), "plannedStart", oldStart, task.getPlannedStart());
        recordIfChanged(actor, plan, task.getId(), "plannedFinish", oldFinish, task.getPlannedFinish());
        recordIfChanged(actor, plan, task.getId(), "durationMinutes", oldDuration, task.getDurationMinutes());
        recordIfChanged(actor, plan, task.getId(), "plannedEffortMinutes", oldEffort,
                task.getPlannedEffortMinutes());
        recordIfChanged(actor, plan, task.getId(), "percentComplete", oldPercent, task.getPercentComplete());
        recordIfChanged(actor, plan, task.getId(), "status", oldStatus, task.getStatus());
        recordIfChanged(actor, plan, task.getId(), "scheduleMode", oldSchedule, task.getScheduleMode());

        auditService.record("PLAN_TASK_UPDATED", "PLAN_TASK", task.getId(),
                Map.of("taskCode", task.getTaskCode(), "wbsCode", task.getWbsCode()));

        log.info("plan-task.update success id={} actor={}", task.getId(), actor.getUsername());
        return taskMapper.toResponse(task);
    }

    private void recordIfChanged(UserPrincipal actor, ProjectPlan plan, UUID taskId, String field,
            Object oldValue, Object newValue) {
        if (!Objects.equals(oldValue, newValue)) {
            changeHistoryService.record(actor, plan, "PLAN_TASK_UPDATED", "PLAN_TASK", taskId,
                    field, oldValue, newValue, null);
        }
    }

    @Transactional
    public void delete(UserPrincipal actor, UUID planId, UUID taskId) {
        ProjectPlan plan = findPlan(planId);
        checkProjectManageAccess(actor, plan.getProject().getId());
        PlanTask task = findTask(planId, taskId);

        if (taskRepository.countByParentIdAndDeletedAtIsNull(task.getId()) > 0) {
            throw new BusinessException(ErrorCode.HAS_CHILDREN,
                    "Task còn task con, không thể xóa");
        }

        task.setDeletedAt(java.time.Instant.now());
        task.setDeletedBy(actor.getId());
        taskRepository.saveAndFlush(task);

        changeHistoryService.record(actor, plan, "PLAN_TASK_DELETED", "PLAN_TASK", task.getId(),
                "deletedAt", task.getTaskCode(), null, null);

        dependencyRepository.deleteByPredecessorId(task.getId());
        dependencyRepository.deleteBySuccessorId(task.getId());

        renumberAndRollup(plan);

        auditService.record("PLAN_TASK_DELETED", "PLAN_TASK", task.getId(),
                Map.of("taskCode", task.getTaskCode()));
        log.info("plan-task.delete success id={} actor={}", task.getId(), actor.getUsername());
    }

    @Transactional
    public PlanTaskResponse move(UserPrincipal actor, UUID planId, UUID taskId, PlanTaskMoveRequest request) {
        ProjectPlan plan = findPlan(planId);
        checkProjectManageAccess(actor, plan.getProject().getId());
        PlanTask task = findTask(planId, taskId);

        List<PlanTask> siblings = siblings(task);
        int index = siblings.indexOf(task);

        switch (request.direction()) {
            case UP -> {
                if (index <= 0) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "Task đã ở đầu danh sách");
                }
                swapSequence(siblings.get(index - 1), task);
            }
            case DOWN -> {
                if (index < 0 || index >= siblings.size() - 1) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "Task đã ở cuối danh sách");
                }
                swapSequence(task, siblings.get(index + 1));
            }
            case INDENT -> {
                if (index <= 0) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "Không có sibling trước để indent");
                }
                PlanTask newParent = siblings.get(index - 1);
                if (!canBeParent(newParent)) {
                    throw new BusinessException(ErrorCode.INVALID_PARENT);
                }
                task.setParent(newParent);
                List<PlanTask> children = groupChildren(newParent, planId);
                children.add(task);
                renumberGroup(children);
            }
            case OUTDENT -> {
                if (task.getParent() == null) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "Task đã ở cấp cao nhất");
                }
                PlanTask oldParent = task.getParent();
                PlanTask grandParent = oldParent.getParent();
                task.setParent(grandParent);
                List<PlanTask> group = groupChildren(grandParent, planId);
                List<PlanTask> newOrder = new ArrayList<>();
                for (PlanTask n : group) {
                    newOrder.add(n);
                    if (n.equals(oldParent)) {
                        newOrder.add(task);
                    }
                }
                renumberGroup(newOrder);
            }
            case TO_PARENT -> {
                PlanTask newParent = null;
                if (request.targetParentId() != null) {
                    newParent = findTask(planId, request.targetParentId());
                }
                validateNotAncestor(task, newParent);
                if (newParent != null && !canBeParent(newParent)) {
                    throw new BusinessException(ErrorCode.INVALID_PARENT);
                }
                task.setParent(newParent);
                List<PlanTask> children = groupChildren(newParent, planId);
                children.add(task);
                renumberGroup(children);
            }
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "direction không hợp lệ");
        }

        taskRepository.saveAndFlush(task);
        renumberAndRollup(plan);

        auditService.record("PLAN_TASK_MOVED", "PLAN_TASK", task.getId(),
                Map.of("taskCode", task.getTaskCode(), "wbsCode", task.getWbsCode(),
                        "direction", request.direction()));
        log.info("plan-task.move success id={} direction={} actor={}",
                task.getId(), request.direction(), actor.getUsername());
        return taskMapper.toResponse(task);
    }

    // ===================== helpers =====================

    private void assignInitialPosition(PlanTask task) {
        int seq = task.getParent() == null
                ? (int) taskRepository.countByPlanIdAndDeletedAtIsNull(task.getPlan().getId()) + 1
                : (int) taskRepository.countByParentIdAndDeletedAtIsNull(task.getParent().getId()) + 1;
        task.setSequenceNumber(seq);
        task.setOutlineLevel(task.getParent() == null ? 1 : task.getParent().getOutlineLevel() + 1);
        task.setWbsCode(task.getParent() == null ? String.valueOf(seq)
                : task.getParent().getWbsCode() + "." + seq);
    }

    private void validateDateRange(PlanTask task) {
        if (task.getPlannedStart() != null && task.getPlannedFinish() != null
                && task.getPlannedFinish().isBefore(task.getPlannedStart())) {
            throw new BusinessException(ErrorCode.INVALID_DATE_RANGE);
        }
    }

    private void applyMilestoneRules(PlanTask task) {
        boolean milestone = task.getTaskType() == PlanTaskType.MILESTONE;
        task.setMilestone(milestone);
        if (milestone) {
            task.setDurationMinutes(0L);
            if (task.getPlannedStart() != null) {
                task.setPlannedFinish(task.getPlannedStart());
            }
            int p = task.getPercentComplete();
            if (p != 0 && p != 100) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Milestone percentComplete chỉ nhận 0 hoặc 100");
            }
        }
    }

    private boolean canBeParent(PlanTask task) {
        return !task.getTaskType().isLeaf();
    }

    private void validateNotAncestor(PlanTask task, PlanTask newParent) {
        if (newParent == null) {
            return;
        }
        PlanTask cursor = newParent;
        while (cursor != null) {
            if (cursor.getId().equals(task.getId())) {
                throw new BusinessException(ErrorCode.CIRCULAR_PARENT);
            }
            cursor = cursor.getParent();
        }
    }

    private List<PlanTask> siblings(PlanTask task) {
        if (task.getParent() != null) {
            return taskRepository.findByParentIdAndDeletedAtIsNull(task.getParent().getId()).stream()
                    .sorted(Comparator.comparingInt(PlanTask::getSequenceNumber))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        }
        return taskRepository.findByPlanIdAndDeletedAtIsNull(task.getPlan().getId()).stream()
                .filter(t -> t.getParent() == null)
                .sorted(Comparator.comparingInt(PlanTask::getSequenceNumber))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private void swapSequence(PlanTask a, PlanTask b) {
        int tmp = a.getSequenceNumber();
        a.setSequenceNumber(b.getSequenceNumber());
        b.setSequenceNumber(tmp);
    }

    private List<PlanTask> groupChildren(PlanTask parent, UUID planId) {
        if (parent == null) {
            return taskRepository.findByPlanIdAndDeletedAtIsNull(planId).stream()
                    .filter(t -> t.getParent() == null)
                    .sorted(Comparator.comparingInt(PlanTask::getSequenceNumber))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        }
        return taskRepository.findByParentIdAndDeletedAtIsNull(parent.getId()).stream()
                .sorted(Comparator.comparingInt(PlanTask::getSequenceNumber))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private void renumberGroup(List<PlanTask> order) {
        for (int i = 0; i < order.size(); i++) {
            order.get(i).setSequenceNumber(i + 1);
        }
    }

    private User resolveOwner(UUID ownerId) {
        if (ownerId == null) {
            return null;
        }
        return userRepository.findById(ownerId)
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng", ownerId));
    }

    private ProjectPlan findPlan(UUID planId) {
        return planRepository.findByIdAndDeletedAtIsNull(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Kế hoạch", planId));
    }

    private PlanTask findTask(UUID planId, UUID taskId) {
        return taskRepository.findByIdAndPlanIdAndDeletedAtIsNull(taskId, planId)
                .orElseThrow(() -> new ResourceNotFoundException("Planning task", taskId));
    }

    /**
     * Renumber toàn cây (sequence 1..n theo sibling, wbsCode 1 / 1.1 / 1.1.2, outlineLevel)
     * + roll-up summary + cập nhật plan (start/finish/progress).
     */
    private void renumberAndRollup(ProjectPlan plan) {
        List<PlanTask> tasks = taskRepository.findByPlanIdAndDeletedAtIsNull(plan.getId());
        if (tasks.isEmpty()) {
            plan.setPlannedStart(null);
            plan.setPlannedFinish(null);
            plan.setProgress(0);
            planRepository.saveAndFlush(plan);
            return;
        }
        Map<UUID, List<PlanTask>> childrenByParent = new HashMap<>();
        for (PlanTask t : tasks) {
            childrenByParent.computeIfAbsent(t.getParent() == null ? null : t.getParent().getId(),
                    k -> new ArrayList<>()).add(t);
        }

        List<PlanTask> roots = childrenByParent.getOrDefault(null, new ArrayList<>());
        roots.sort(Comparator.comparingInt(PlanTask::getSequenceNumber));

        List<PlanTask> ordered = new ArrayList<>();
        renumberSubtree(roots, null, ordered, childrenByParent);

        rollupTree(roots, childrenByParent);

        taskRepository.saveAll(ordered);
        taskRepository.flush();

        updatePlanRollup(plan, roots, childrenByParent);
        if (plan.getParentMilestoneTaskId() != null) {
            syncParentMilestone(plan);
        }
    }

    private void syncParentMilestone(ProjectPlan detailPlan) {
        PlanTask milestoneTask = taskRepository.findByIdAndDeletedAtIsNull(detailPlan.getParentMilestoneTaskId()).orElse(null);
        if (milestoneTask == null) {
            return;
        }
        List<PlanTask> leafTasks = taskRepository.findByPlanIdAndDeletedAtIsNull(detailPlan.getId()).stream()
                .filter(t -> !t.isSummary())
                .toList();
        if (!leafTasks.isEmpty()) {
            LocalDate minStart = leafTasks.stream()
                    .map(PlanTask::getPlannedStart)
                    .filter(Objects::nonNull)
                    .min(LocalDate::compareTo)
                    .orElse(null);
            LocalDate maxFinish = leafTasks.stream()
                    .map(PlanTask::getPlannedFinish)
                    .filter(Objects::nonNull)
                    .max(LocalDate::compareTo)
                    .orElse(null);
            long totalDuration = 0;
            long weightedProgressSum = 0;
            for (PlanTask t : leafTasks) {
                long dur = t.getDurationMinutes() != null ? t.getDurationMinutes() : 0;
                totalDuration += dur;
                weightedProgressSum += dur * t.getPercentComplete();
            }
            int progress = totalDuration > 0 ? (int) (weightedProgressSum / totalDuration) : 0;
            milestoneTask.setPlannedStart(minStart);
            milestoneTask.setPlannedFinish(maxFinish);
            milestoneTask.setPercentComplete(progress);
            if (progress >= 100) {
                milestoneTask.setStatus(PlanTaskStatus.COMPLETED);
            } else if (progress > 0) {
                milestoneTask.setStatus(PlanTaskStatus.IN_PROGRESS);
            } else {
                milestoneTask.setStatus(PlanTaskStatus.NOT_STARTED);
            }
            taskRepository.save(milestoneTask);
            taskRepository.flush();
            if (!Objects.equals(milestoneTask.getPlan().getId(), detailPlan.getId())) {
                renumberAndRollup(milestoneTask.getPlan());
            }
        }
    }

    private void renumberSubtree(List<PlanTask> nodes, PlanTask parent,
            List<PlanTask> ordered, Map<UUID, List<PlanTask>> childrenByParent) {
        int seq = 1;
        for (PlanTask node : nodes) {
            node.setSequenceNumber(seq);
            node.setOutlineLevel(parent == null ? 1 : parent.getOutlineLevel() + 1);
            node.setWbsCode(parent == null ? String.valueOf(seq)
                    : parent.getWbsCode() + "." + seq);
            ordered.add(node);
            List<PlanTask> children = childrenByParent.getOrDefault(node.getId(), new ArrayList<>());
            children.sort(Comparator.comparingInt(PlanTask::getSequenceNumber));
            renumberSubtree(children, node, ordered, childrenByParent);
            seq++;
        }
    }

    private void rollupTree(List<PlanTask> roots, Map<UUID, List<PlanTask>> childrenByParent) {
        for (PlanTask root : roots) {
            rollupNode(root, childrenByParent);
        }
    }

    private void rollupNode(PlanTask node, Map<UUID, List<PlanTask>> childrenByParent) {
        List<PlanTask> children = childrenByParent.getOrDefault(node.getId(), new ArrayList<>());
        for (PlanTask child : children) {
            rollupNode(child, childrenByParent);
        }
        boolean summary = !children.isEmpty();
        node.setSummary(summary);
        if (summary) {
            int effort = children.stream()
                    .mapToInt(c -> c.getPlannedEffortMinutes() == null ? 0 : c.getPlannedEffortMinutes())
                    .sum();
            long duration = children.stream()
                    .mapToLong(c -> c.getDurationMinutes() == null ? 0 : c.getDurationMinutes())
                    .sum();
            node.setPlannedEffortMinutes(effort);
            node.setDurationMinutes(duration);
            node.setPlannedStart(children.stream()
                    .map(PlanTask::getPlannedStart)
                    .filter(Objects::nonNull)
                    .min(Comparator.naturalOrder())
                    .orElse(null));
            node.setPlannedFinish(children.stream()
                    .map(PlanTask::getPlannedFinish)
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(null));
            node.setPercentComplete(rollupProgress(children));
        }
    }

    private int rollupProgress(List<PlanTask> children) {
        long effort = children.stream()
                .mapToLong(c -> c.getPlannedEffortMinutes() == null ? 0 : c.getPlannedEffortMinutes()).sum();
        if (effort > 0) {
            long weight = children.stream()
                    .mapToLong(c -> (long) c.getPercentComplete()
                            * (c.getPlannedEffortMinutes() == null ? 0 : c.getPlannedEffortMinutes()))
                    .sum();
            return (int) Math.round((double) weight / effort);
        }
        long duration = children.stream()
                .mapToLong(c -> c.getDurationMinutes() == null ? 0 : c.getDurationMinutes()).sum();
        if (duration > 0) {
            long weight = children.stream()
                    .mapToLong(c -> (long) c.getPercentComplete()
                            * (c.getDurationMinutes() == null ? 0 : c.getDurationMinutes()))
                    .sum();
            return (int) Math.round((double) weight / duration);
        }
        int sum = children.stream().mapToInt(PlanTask::getPercentComplete).sum();
        return sum / children.size();
    }

    private void updatePlanRollup(ProjectPlan plan, List<PlanTask> roots,
            Map<UUID, List<PlanTask>> childrenByParent) {
        LocalDate minStart = roots.stream()
                .map(PlanTask::getPlannedStart)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
        LocalDate maxFinish = roots.stream()
                .map(PlanTask::getPlannedFinish)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        if (minStart != null) {
            plan.setPlannedStart(minStart);
        }
        if (maxFinish != null) {
            plan.setPlannedFinish(maxFinish);
        }
        plan.setProgress(rollupProgress(roots));
        planRepository.saveAndFlush(plan);
    }

    private Comparator<PlanTask> wbsComparator() {
        return Comparator.comparing(PlanTask::getWbsCode, (a, b) -> {
            String[] pa = a.split("\\.");
            String[] pb = b.split("\\.");
            int len = Math.min(pa.length, pb.length);
            for (int i = 0; i < len; i++) {
                int cmp = Integer.compare(Integer.parseInt(pa[i]), Integer.parseInt(pb[i]));
                if (cmp != 0) {
                    return cmp;
                }
            }
            return Integer.compare(pa.length, pb.length);
        });
    }

    private void checkProjectViewAccess(UserPrincipal actor, UUID projectId) {
        if (actor.getRoles().contains("ADMIN")) {
            return;
        }
        if (!memberRepository.existsByProjectIdAndUser_Id(projectId, actor.getId())) {
            throw new AccessDeniedException("Access denied to project");
        }
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
