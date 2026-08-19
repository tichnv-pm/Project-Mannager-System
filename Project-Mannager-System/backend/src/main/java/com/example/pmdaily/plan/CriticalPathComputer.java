package com.example.pmdaily.plan;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Critical Path Method (CPM) — docs/planning/09, PLN-FR-CP-01..05, PLN-AC-CP-01..05.
 * <p>
 * Forward pass lấy ES/EF từ kết quả scheduling (early start/finish). Backward pass
 * tính LS/LF theo thứ tự topo đảo với cùng quy ước lag/working-day của SchedulingEngine
 * (docs/planning/08 muc 3.2): FS -> minusWorkingDays(LS(succ), lag+1), SS -> minusWorkingDays(LS(succ), lag),
 * FF -> minusWorkingDays(LF(succ), lag), SF -> minusWorkingDays(LF(succ), lag).
 * TotalFloat = số ngày làm việc giữa ES và LS (exclusive ES); critical khi TotalFloat <= threshold
 * (mặc định 0 phút, PLN-RULE-CP-01). Task MANUAL / MILESTONE được tính như task thường
 * (PLN-RULE-CP-03, PLN-AC-CP-03); summary lấy giá trị từ roll-up con (bỏ qua).
 * </p>
 */
final class CriticalPathComputer {

    /** Kết quả CPM của một task. */
    record TaskData(
            LocalDate earlyStart,
            LocalDate earlyFinish,
            LocalDate lateStart,
            LocalDate lateFinish,
            long totalFloatMinutes,
            long freeFloatMinutes,
            boolean critical,
            Integer pathId
    ) {}

    static Map<UUID, TaskData> compute(List<PlanTask> tasks, List<PlanTaskDependency> dependencies,
            WorkingCalendar calendar, LocalDate planStart, LocalDate planFinish, int thresholdMinutes) {
        Map<UUID, TaskData> result = new HashMap<>();

        Map<UUID, PlanTask> byId = new HashMap<>();
        Map<UUID, List<PlanTaskDependency>> succDeps = new HashMap<>();
        Map<UUID, List<PlanTaskDependency>> predDeps = new HashMap<>();
        for (PlanTask t : tasks) {
            byId.put(t.getId(), t);
        }
        for (PlanTaskDependency dep : dependencies) {
            succDeps.computeIfAbsent(dep.getPredecessor().getId(), k -> new ArrayList<>()).add(dep);
            predDeps.computeIfAbsent(dep.getSuccessor().getId(), k -> new ArrayList<>()).add(dep);
        }

        List<PlanTask> involved = tasks.stream()
                .filter(t -> !t.isSummary())
                .filter(t -> t.getPlannedStart() != null && t.getPlannedFinish() != null)
                .toList();
        if (involved.isEmpty()) {
            return result;
        }
        if (planFinish == null) {
            planFinish = involved.stream().map(PlanTask::getPlannedFinish)
                    .max(Comparator.naturalOrder()).orElse(null);
        }
        if (planFinish == null) {
            return result;
        }

        // early dates từ scheduling
        Map<UUID, LocalDate> es = new HashMap<>();
        Map<UUID, LocalDate> ef = new HashMap<>();
        for (PlanTask t : involved) {
            es.put(t.getId(), t.getPlannedStart());
            ef.put(t.getId(), t.getPlannedFinish());
        }

        // backward pass: successor trước predecessor
        List<PlanTask> topo = topological(involved, predDeps);
        Map<UUID, LocalDate> ls = new HashMap<>();
        Map<UUID, LocalDate> lf = new HashMap<>();
        for (int i = topo.size() - 1; i >= 0; i--) {
            PlanTask t = topo.get(i);
            List<PlanTaskDependency> succ = succDeps.getOrDefault(t.getId(), List.of());
            if (succ.isEmpty()) {
                LocalDate lfVal = planFinish;
                LocalDate lsVal;
                if (t.isMilestone()) {
                    lsVal = lfVal;
                } else {
                    int durDays = durationDays(t, calendar);
                    lsVal = durDays <= 1 ? lfVal : calendar.minusWorkingDays(lfVal, durDays - 1);
                }
                lf.put(t.getId(), lfVal);
                ls.put(t.getId(), lsVal);
                continue;
            }
            LocalDate latestFinish = null;
            LocalDate latestStart = null;
            boolean bounded = false;
            for (PlanTaskDependency dep : succ) {
                PlanTask successor = byId.get(dep.getSuccessor().getId());
                if (successor == null || !ls.containsKey(successor.getId())) {
                    continue;
                }
                bounded = true;
                int lag = lagDays(dep, calendar);
                switch (dep.getDependencyType()) {
                    case FS -> latestFinish = minDate(latestFinish,
                            calendar.minusWorkingDays(ls.get(successor.getId()), lag + 1));
                    case FF -> latestFinish = minDate(latestFinish,
                            calendar.minusWorkingDays(lf.get(successor.getId()), lag));
                    case SS -> latestStart = maxDate(latestStart,
                            calendar.minusWorkingDays(ls.get(successor.getId()), lag));
                    case SF -> latestStart = maxDate(latestStart,
                            calendar.minusWorkingDays(lf.get(successor.getId()), lag));
                }
            }
            LocalDate lfVal = bounded && latestFinish != null ? latestFinish : planFinish;
            LocalDate lsVal;
            if (t.isMilestone()) {
                lsVal = lfVal;
            } else {
                int durDays = durationDays(t, calendar);
                LocalDate fromFinish = durDays <= 1 ? lfVal : calendar.minusWorkingDays(lfVal, durDays - 1);
                lsVal = latestStart != null && latestStart.isAfter(fromFinish) ? latestStart : fromFinish;
                if (lsVal.isAfter(lfVal)) {
                    lsVal = lfVal;
                }
            }
            lf.put(t.getId(), lfVal);
            ls.put(t.getId(), lsVal);
        }

        // float + critical
        Map<UUID, TaskData> data = new HashMap<>();
        for (PlanTask t : involved) {
            LocalDate lateStart = ls.get(t.getId());
            LocalDate lateFinish = lf.get(t.getId());
            if (lateStart == null || lateFinish == null) {
                continue;
            }
            LocalDate earlyStart = es.get(t.getId());
            LocalDate earlyFinish = ef.get(t.getId());

            long totalFloat = (long) calendar.workingDaysBetween(
                    earlyStart.plusDays(1), lateStart.plusDays(1)) * calendar.dailyMinutes();
            LocalDate minSuccessorStart = succDeps.getOrDefault(t.getId(), List.of()).stream()
                    .map(d -> es.get(d.getSuccessor().getId()))
                    .filter(Objects::nonNull)
                    .min(Comparator.naturalOrder())
                    .orElse(null);
            long freeFloat = minSuccessorStart != null && minSuccessorStart.isAfter(earlyFinish)
                    ? Math.max(0L, (long) calendar.workingDaysBetween(
                            earlyFinish.plusDays(1), minSuccessorStart) * calendar.dailyMinutes())
                    : 0L;

            boolean critical = totalFloat <= thresholdMinutes;
            data.put(t.getId(), new TaskData(earlyStart, earlyFinish, lateStart, lateFinish,
                    totalFloat, freeFloat, critical, null));
        }

        assignPathIds(tasks, dependencies, data, thresholdMinutes);
        return data;
    }

    /** Nhóm critical task nối nhau bởi dependency thành các path (connected component). */
    private static void assignPathIds(List<PlanTask> tasks, List<PlanTaskDependency> dependencies,
            Map<UUID, TaskData> data, int thresholdMinutes) {
        Map<UUID, Integer> pathId = new HashMap<>();
        int nextPath = 1;
        List<PlanTask> ordered = tasks.stream()
                .filter(t -> {
                    TaskData d = data.get(t.getId());
                    return d != null && d.critical();
                })
                .sorted(Comparator.comparing(t -> data.get(t.getId()).earlyStart()))
                .toList();
        for (PlanTask t : ordered) {
            if (pathId.containsKey(t.getId())) {
                continue;
            }
            int id = nextPath++;
            ArrayDeque<UUID> queue = new ArrayDeque<>();
            queue.add(t.getId());
            pathId.put(t.getId(), id);
            while (!queue.isEmpty()) {
                UUID current = queue.poll();
                for (PlanTaskDependency dep : dependencies) {
                    boolean forward = dep.getPredecessor().getId().equals(current)
                            && data.containsKey(dep.getSuccessor().getId())
                            && data.get(dep.getSuccessor().getId()).critical();
                    boolean backward = dep.getSuccessor().getId().equals(current)
                            && data.containsKey(dep.getPredecessor().getId())
                            && data.get(dep.getPredecessor().getId()).critical();
                    UUID next = forward ? dep.getSuccessor().getId()
                            : backward ? dep.getPredecessor().getId() : null;
                    if (next != null && !pathId.containsKey(next)) {
                        pathId.put(next, id);
                        queue.add(next);
                    }
                }
            }
        }
        data.replaceAll((id, d) -> !d.critical()
                ? d
                : new TaskData(d.earlyStart(), d.earlyFinish(), d.lateStart(), d.lateFinish(),
                        d.totalFloatMinutes(), d.freeFloatMinutes(), true, pathId.get(id)));
    }

    /** Kahn topological order theo predecessor (node không predecessor trước). */
    private static List<PlanTask> topological(List<PlanTask> tasks, Map<UUID, List<PlanTaskDependency>> predDeps) {
        Map<UUID, Integer> indegree = new HashMap<>();
        Map<UUID, List<UUID>> children = new HashMap<>();
        for (PlanTask t : tasks) {
            indegree.put(t.getId(), 0);
        }
        for (PlanTask t : tasks) {
            for (PlanTaskDependency dep : predDeps.getOrDefault(t.getId(), List.of())) {
                if (!indegree.containsKey(dep.getPredecessor().getId())) {
                    continue;
                }
                indegree.merge(t.getId(), 1, Integer::sum);
                children.computeIfAbsent(dep.getPredecessor().getId(), k -> new ArrayList<>())
                        .add(t.getId());
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

    private static int durationDays(PlanTask task, WorkingCalendar calendar) {
        if (task.isMilestone()) {
            return 0;
        }
        if (task.getPlannedStart() != null && task.getPlannedFinish() != null) {
            int days = calendar.workingDaysInSpan(task.getPlannedStart(), task.getPlannedFinish());
            if (days > 0) {
                return days;
            }
        }
        long duration = task.getDurationMinutes() == null
                ? (task.getPlannedEffortMinutes() == null ? 480L : task.getPlannedEffortMinutes())
                : task.getDurationMinutes();
        return Math.max(1, (int) Math.ceil(duration / (double) calendar.dailyMinutes()));
    }

    private static int lagDays(PlanTaskDependency dep, WorkingCalendar calendar) {
        int lag = dep.getLagMinutes();
        if (lag == 0) {
            return 0;
        }
        return (int) Math.ceil(lag / (double) calendar.dailyMinutes());
    }

    private static LocalDate minDate(LocalDate a, LocalDate b) {
        return a == null ? b : (b != null && b.isBefore(a) ? b : a);
    }

    private static LocalDate maxDate(LocalDate a, LocalDate b) {
        return a == null ? b : (b != null && b.isAfter(a) ? b : a);
    }
}