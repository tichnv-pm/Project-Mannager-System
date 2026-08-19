package com.example.pmdaily.plan;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.pmdaily.exception.ResourceNotFoundException;
import com.example.pmdaily.plan.dto.RecalcResponse;
import com.example.pmdaily.plan.dto.RecalcResponse.SchedulingWarningDto;

/**
 * Scheduling Engine (docs/planning/08) — PLN-FR-SCHED-01..06, PLN-AC-SCHED-01..05, PLN-RULE-SCHED-01..08.
 * <p>
 * Forward pass theo topo (dependency first): task AUTO tính start/finish theo
 * (predecessor + lag + duration + working calendar); working calendar loại trừ
 * weekend/holiday (exception WORKING biến ngày thường thành ngày làm việc);
 * MILESTONE duration = 0 (finish = start); MANUAL / FIXED_DATE / REMOVE_SCHEDULE giữ nguyên;
 * summary + plan roll-up min/max sau khi tính xong lá. Kết quả idempotent.
 * </p>
 */
@Service
public class SchedulingEngine {

    private static final Logger log = LoggerFactory.getLogger(SchedulingEngine.class);

    private static final int DEFAULT_TASK_DURATION_MINUTES = 480;

    private final PlanCalendarRepository calendarRepository;
    private final PlanCalendarWorkingDayRepository workingDayRepository;
    private final PlanCalendarExceptionRepository exceptionRepository;
    private final ProjectPlanRepository planRepository;
    private final PlanTaskRepository taskRepository;
    private final PlanTaskDependencyRepository dependencyRepository;

    public SchedulingEngine(
            PlanCalendarRepository calendarRepository,
            PlanCalendarWorkingDayRepository workingDayRepository,
            PlanCalendarExceptionRepository exceptionRepository,
            ProjectPlanRepository planRepository,
            PlanTaskRepository taskRepository,
            PlanTaskDependencyRepository dependencyRepository) {
        this.calendarRepository = calendarRepository;
        this.workingDayRepository = workingDayRepository;
        this.exceptionRepository = exceptionRepository;
        this.planRepository = planRepository;
        this.taskRepository = taskRepository;
        this.dependencyRepository = dependencyRepository;
    }

    /**
     * Lập lịch toàn bộ plan (synchronous trong request — plan ≤ 200 task theo
     * docs/planning/08 muc 4; async job trên bảng plan_recalc_jobs thuộc backlog).
     */
    @Transactional
    public RecalcResponse recalculate(UUID planId) {
        ProjectPlan plan = planRepository.findByIdAndDeletedAtIsNull(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Kế hoạch", planId));

        List<PlanTask> tasks = taskRepository.findByPlanIdAndDeletedAtIsNull(planId);
        List<PlanTaskDependency> dependencies = dependencyRepository.findByPlan_Id(planId);

        Map<UUID, PlanTask> byId = new HashMap<>();
        for (PlanTask t : tasks) {
            byId.put(t.getId(), t);
        }

        ScheduleContext ctx = new ScheduleContext(buildCalendar(plan), byId, plan.getPlannedStart());

        Map<UUID, Set<UUID>> dependsOn = new HashMap<>();
        Map<String, PlanTaskDependency> depByPair = new HashMap<>();
        for (PlanTaskDependency dep : dependencies) {
            dependsOn.computeIfAbsent(dep.getSuccessor().getId(), k -> new LinkedHashSet<>())
                    .add(dep.getPredecessor().getId());
            depByPair.put(pairKey(dep.getPredecessor().getId(), dep.getSuccessor().getId()), dep);
            if (dep.getLagMinutes() < 0) {
                ctx.addWarning(dep.getSuccessor().getWbsCode(), SchedulingWarningType.NEGATIVE_LAG,
                        "Lag âm (" + dep.getLagMinutes() + " phút) được phép — lịch tính overlap");
            }
        }

        if (hasCycle(tasks, dependsOn)) {
            ctx.addWarning(null, SchedulingWarningType.CYCLE_DEPENDENCY,
                    "Phát hiện vòng lặp dependency — các task trong cycle được giữ nguyên");
        }

        int scheduled = 0;
        for (PlanTask task : topologicalOrder(tasks, dependsOn)) {
            if (shouldSchedule(task) && scheduleTask(task, dependsOn, depByPair, ctx)) {
                scheduled++;
            }
        }

        rollupSummaryAndPlan(plan, tasks, ctx);
        persistCriticalSnapshot(plan, tasks, dependencies, ctx.calendar);

        taskRepository.saveAll(tasks);
        taskRepository.flush();
        planRepository.saveAndFlush(plan);

        log.info("schedule.recalc planId={} total={} scheduled={} warnings={}",
                planId, tasks.size(), scheduled, ctx.warnings.size());
        return new RecalcResponse(plan.getId(), plan.getPlannedStart(), plan.getPlannedFinish(),
                plan.getDurationMinutes(), tasks.size(), scheduled, ctx.warnings);
    }

    // ===================== scheduling =====================

    private boolean shouldSchedule(PlanTask task) {
        if (task.isSummary()) {
            return false; // summary: ngày lấy từ roll-up children (PLN-RULE-SCHED-05)
        }
        if (task.getScheduleMode() != ScheduleMode.AUTO) {
            return false;
        }
        ConstraintType constraint = task.getConstraintType();
        return constraint != ConstraintType.FIXED_DATE && constraint != ConstraintType.REMOVE_SCHEDULE;
    }

    private boolean scheduleTask(PlanTask task, Map<UUID, Set<UUID>> dependsOn,
            Map<String, PlanTaskDependency> depByPair, ScheduleContext ctx) {
        LocalDate anchor = null;

        for (UUID predId : dependsOn.getOrDefault(task.getId(), Set.of())) {
            PlanTask pred = ctx.tasks.get(predId);
            if (pred == null || pred.getPlannedStart() == null) {
                continue;
            }
            LocalDate predStart = pred.getPlannedStart();
            LocalDate predFinish = pred.getPlannedFinish() != null
                    ? pred.getPlannedFinish() : predStart;
            PlanTaskDependency dep = depByPair.get(pairKey(predId, task.getId()));
            int lagDays = lagDays(dep, ctx);
            LocalDate candidate = switch (dep.getDependencyType()) {
                case FS -> ctx.calendar.afterWorkingDays(predFinish, lagDays);
                case SS -> ctx.calendar.inclusivePlusWorkingDays(predStart, lagDays);
                case FF -> shiftFinishConstraint(ctx, task, predFinish, lagDays);
                case SF -> shiftFinishConstraint(ctx, task, predStart, lagDays);
            };
            if (candidate != null && (anchor == null || candidate.isAfter(anchor))) {
                anchor = candidate;
            }
        }

        if (anchor == null) {
            anchor = task.getPlannedStart() != null ? task.getPlannedStart() : ctx.planStart;
            if (anchor == null) {
                ctx.addWarning(task.getWbsCode(), SchedulingWarningType.NO_START_ANCHOR,
                        "Task " + task.getWbsCode() + " không có predecessor, không có plan start và chưa có ngày");
                return false;
            }
        }

        if (task.getConstraintType() == ConstraintType.START_NO_EARLIER_THAN
                && task.getConstraintDate() != null && anchor.isBefore(task.getConstraintDate())) {
            anchor = task.getConstraintDate();
        }
        if (task.getConstraintType() == ConstraintType.START_NO_LATER_THAN
                && task.getConstraintDate() != null && anchor.isAfter(task.getConstraintDate())) {
            ctx.addWarning(task.getWbsCode(), SchedulingWarningType.CONSTRAINT_CONFLICT,
                    "Constraint START_NO_LATER_THAN " + task.getConstraintDate() + " bị vi phạm bởi dependency");
        }

        LocalDate start = ctx.calendar.nextWorkingDate(anchor);
        if (!start.equals(anchor)) {
            ctx.addWarning(task.getWbsCode(), SchedulingWarningType.DATE_NOT_WORKING,
                    "Start " + anchor + " rơi vào ngày nghỉ/lễ — đẩy sang " + start);
        }

        int durationDays = durationDays(task, ctx);
        LocalDate finish = task.isMilestone() ? start : ctx.calendar.inclusiveEnd(start, durationDays);

        task.setPlannedStart(start);
        task.setPlannedFinish(finish);
        return true;
    }

    /** FF/SF: finish ≥ reference + lag → quy về start (lùi durationDays - 1 ngày làm việc). */
    private LocalDate shiftFinishConstraint(ScheduleContext ctx, PlanTask task,
            LocalDate reference, int lagDays) {
        LocalDate finishPoint = ctx.calendar.inclusivePlusWorkingDays(reference, lagDays);
        int durationDays = durationDays(task, ctx);
        return durationDays <= 0 ? finishPoint
                : ctx.calendar.minusWorkingDays(finishPoint, durationDays - 1);
    }

    private int durationDays(PlanTask task, ScheduleContext ctx) {
        if (task.isMilestone()) {
            return 0;
        }
        long duration = task.getDurationMinutes() == null
                ? (task.getPlannedEffortMinutes() == null
                        ? DEFAULT_TASK_DURATION_MINUTES : task.getPlannedEffortMinutes())
                : task.getDurationMinutes();
        return Math.max(1, (int) Math.ceil(duration / (double) ctx.calendar.dailyMinutes()));
    }

    private int lagDays(PlanTaskDependency dep, ScheduleContext ctx) {
        if (dep.getLagMinutes() == 0) {
            return 0;
        }
        double days = dep.getLagMinutes() / (double) ctx.calendar.dailyMinutes();
        return (int) Math.ceil(days);
    }

    // ===================== critical snapshot =====================

    /** Chụp is_critical (CPM) sau recalc — PLN-AC-CP-04 (docs/planning/09 muc 5: không phải input). */
    private void persistCriticalSnapshot(ProjectPlan plan, List<PlanTask> tasks,
            List<PlanTaskDependency> dependencies, WorkingCalendar calendar) {
        Map<UUID, CriticalPathComputer.TaskData> data = CriticalPathComputer.compute(
                tasks, dependencies, calendar, plan.getPlannedStart(), plan.getPlannedFinish(),
                CriticalPathService.CRITICAL_THRESHOLD_MINUTES);
        for (PlanTask task : tasks) {
            CriticalPathComputer.TaskData d = data.get(task.getId());
            task.setCritical(d != null && d.critical());
        }
    }

    // ===================== order & cycle =====================

    private boolean hasCycle(List<PlanTask> tasks, Map<UUID, Set<UUID>> dependsOn) {
        Map<UUID, Integer> state = new HashMap<>();
        for (PlanTask t : tasks) {
            if (cycleDfs(t.getId(), dependsOn, state)) {
                return true;
            }
        }
        return false;
    }

    private boolean cycleDfs(UUID node, Map<UUID, Set<UUID>> dependsOn, Map<UUID, Integer> state) {
        int marker = state.getOrDefault(node, 0);
        if (marker == 2) {
            return false;
        }
        if (marker == 1) {
            return true;
        }
        state.put(node, 1);
        for (UUID pred : dependsOn.getOrDefault(node, Set.of())) {
            if (cycleDfs(pred, dependsOn, state)) {
                return true;
            }
        }
        state.put(node, 2);
        return false;
    }

    /** Kahn topological order; nếu còn sót (cycle) thì đẩy nốt các node còn lại. */
    private List<PlanTask> topologicalOrder(List<PlanTask> tasks, Map<UUID, Set<UUID>> dependsOn) {
        Map<UUID, Integer> indegree = new HashMap<>();
        Map<UUID, List<UUID>> children = new HashMap<>();
        for (PlanTask t : tasks) {
            indegree.putIfAbsent(t.getId(), 0);
        }
        for (PlanTask t : tasks) {
            for (UUID pred : dependsOn.getOrDefault(t.getId(), Set.of())) {
                if (!indegree.containsKey(pred)) {
                    continue;
                }
                indegree.merge(t.getId(), 1, Integer::sum);
                children.computeIfAbsent(pred, k -> new ArrayList<>()).add(t.getId());
            }
        }
        List<PlanTask> order = new ArrayList<>();
        Set<UUID> done = new HashSet<>();
        while (done.size() < tasks.size()) {
            boolean progress = false;
            for (PlanTask t : tasks) {
                if (!done.contains(t.getId()) && indegree.getOrDefault(t.getId(), 0) == 0) {
                    order.add(t);
                    done.add(t.getId());
                    progress = true;
                    for (UUID child : children.getOrDefault(t.getId(), List.of())) {
                        indegree.merge(child, -1, Integer::sum);
                    }
                }
            }
            if (!progress) {
                for (PlanTask t : tasks) {
                    if (!done.contains(t.getId())) {
                        order.add(t);
                        done.add(t.getId());
                    }
                }
            }
        }
        return order;
    }

    // ===================== roll-up =====================

    private void rollupSummaryAndPlan(ProjectPlan plan, List<PlanTask> tasks, ScheduleContext ctx) {
        Map<UUID, List<PlanTask>> childrenByParent = new HashMap<>();
        for (PlanTask t : tasks) {
            childrenByParent.computeIfAbsent(t.getParent() == null ? null : t.getParent().getId(),
                    k -> new ArrayList<>()).add(t);
        }

        for (PlanTask node : tasks) {
            List<PlanTask> children = childrenByParent.getOrDefault(node.getId(), List.of());
            if (children.isEmpty()) {
                continue;
            }
            LocalDate minStart = children.stream().map(PlanTask::getPlannedStart)
                    .filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
            LocalDate maxFinish = children.stream().map(PlanTask::getPlannedFinish)
                    .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
            node.setPlannedStart(minStart);
            node.setPlannedFinish(maxFinish);
        }

        List<PlanTask> roots = childrenByParent.getOrDefault(null, List.of());
        LocalDate minStart = roots.stream().map(PlanTask::getPlannedStart)
                .filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
        LocalDate maxFinish = roots.stream().map(PlanTask::getPlannedFinish)
                .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        plan.setPlannedStart(minStart);
        plan.setPlannedFinish(maxFinish);
        if (minStart != null && maxFinish != null) {
            plan.setDurationMinutes((long) ctx.calendar.workingDaysInSpan(minStart, maxFinish)
                    * ctx.calendar.dailyMinutes());
        } else {
            plan.setDurationMinutes(null);
        }
    }

    private static String pairKey(UUID predId, UUID succId) {
        return predId + "->" + succId;
    }

    // ===================== calendar =====================

    private WorkingCalendar buildCalendar(ProjectPlan plan) {
        return WorkingCalendar.build(plan, calendarRepository, workingDayRepository, exceptionRepository);
    }

    private record ScheduleContext(WorkingCalendar calendar, Map<UUID, PlanTask> tasks,
            LocalDate planStart, List<SchedulingWarningDto> warnings) {

        ScheduleContext(WorkingCalendar calendar, Map<UUID, PlanTask> tasks, LocalDate planStart) {
            this(calendar, tasks, planStart, new ArrayList<>());
        }

        void addWarning(String wbsCode, SchedulingWarningType type, String message) {
            warnings.add(new SchedulingWarningDto(wbsCode, type, message));
        }
    }
}
