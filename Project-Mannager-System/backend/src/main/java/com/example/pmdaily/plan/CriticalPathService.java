package com.example.pmdaily.plan;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.pmdaily.exception.ResourceNotFoundException;
import com.example.pmdaily.plan.dto.CriticalPathResult;
import com.example.pmdaily.plan.dto.CriticalPathResult.CriticalTaskDto;

/**
 * Critical path on-demand (docs/planning/09) — PLN-FR-CP-01..05, PLN-RULE-CP-01..04.
 * Tính live mỗi lần gọi, KHÔNG lưu kết quả (chỉ recalc lưu snapshot is_critical — PLN-AC-CP-04).
 */
@Service
public class CriticalPathService {

    private static final Logger log = LoggerFactory.getLogger(CriticalPathService.class);

    /** Threshold float criticality — mặc định 0 (PLN-RULE-CP-01); config hệ thống thuộc backlog. */
    static final int CRITICAL_THRESHOLD_MINUTES = 0;

    private final ProjectPlanRepository planRepository;
    private final PlanTaskRepository taskRepository;
    private final PlanTaskDependencyRepository dependencyRepository;
    private final PlanCalendarRepository calendarRepository;
    private final PlanCalendarWorkingDayRepository workingDayRepository;
    private final PlanCalendarExceptionRepository exceptionRepository;

    public CriticalPathService(
            ProjectPlanRepository planRepository,
            PlanTaskRepository taskRepository,
            PlanTaskDependencyRepository dependencyRepository,
            PlanCalendarRepository calendarRepository,
            PlanCalendarWorkingDayRepository workingDayRepository,
            PlanCalendarExceptionRepository exceptionRepository) {
        this.planRepository = planRepository;
        this.taskRepository = taskRepository;
        this.dependencyRepository = dependencyRepository;
        this.calendarRepository = calendarRepository;
        this.workingDayRepository = workingDayRepository;
        this.exceptionRepository = exceptionRepository;
    }

    @Transactional(readOnly = true)
    public CriticalPathResult calculate(UUID planId) {
        ProjectPlan plan = planRepository.findByIdAndDeletedAtIsNull(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Kế hoạch", planId));

        List<PlanTask> tasks = taskRepository.findByPlanIdAndDeletedAtIsNull(planId);
        List<PlanTaskDependency> dependencies = dependencyRepository.findByPlan_Id(planId);
        WorkingCalendar calendar = WorkingCalendar.build(plan, calendarRepository,
                workingDayRepository, exceptionRepository);

        java.time.LocalDate planFinish = plan.getPlannedFinish();
        Map<UUID, CriticalPathComputer.TaskData> data = CriticalPathComputer.compute(
                tasks, dependencies, calendar, plan.getPlannedStart(), planFinish,
                CRITICAL_THRESHOLD_MINUTES);

        List<CriticalTaskDto> resultTasks = tasks.stream()
                .filter(t -> data.containsKey(t.getId()))
                .sorted(Comparator.comparingInt(PlanTask::getOutlineLevel)
                        .thenComparingInt(PlanTask::getSequenceNumber))
                .map(t -> new CriticalTaskDto(t.getId(), t.getWbsCode(), t.getTaskName(), t.getTaskType(),
                        data.get(t.getId()).earlyStart(), data.get(t.getId()).earlyFinish(),
                        data.get(t.getId()).lateStart(), data.get(t.getId()).lateFinish(),
                        data.get(t.getId()).totalFloatMinutes(), data.get(t.getId()).freeFloatMinutes(),
                        data.get(t.getId()).critical(), data.get(t.getId()).pathId()))
                .toList();

        long criticalCount = resultTasks.stream().filter(CriticalTaskDto::isCritical).count();
        Long totalDuration = plan.getPlannedStart() != null && planFinish != null
                ? (long) calendar.workingDaysInSpan(plan.getPlannedStart(), planFinish)
                        * calendar.dailyMinutes()
                : null;

        log.info("plan.critical-path planId={} tasks={} critical={} actor-view",
                planId, resultTasks.size(), criticalCount);
        return new CriticalPathResult(planId, plan.getPlannedStart(), planFinish,
                totalDuration, CRITICAL_THRESHOLD_MINUTES, (int) criticalCount, resultTasks);
    }
}