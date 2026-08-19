package com.example.pmdaily.project;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.pmdaily.plan.PlanStatus;
import com.example.pmdaily.plan.PlanTask;
import com.example.pmdaily.plan.PlanTaskRepository;
import com.example.pmdaily.plan.ProjectPlan;
import com.example.pmdaily.plan.ProjectPlanRepository;
import com.example.pmdaily.user.User;

@Service
public class EvmScheduler {

    private static final Logger log = LoggerFactory.getLogger(EvmScheduler.class);

    private final ProjectRepository projectRepository;
    private final ProjectPlanRepository projectPlanRepository;
    private final PlanTaskRepository planTaskRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectFinancialSnapshotRepository snapshotRepository;

    public EvmScheduler(ProjectRepository projectRepository,
                        ProjectPlanRepository projectPlanRepository,
                        PlanTaskRepository planTaskRepository,
                        ProjectMemberRepository projectMemberRepository,
                        ProjectFinancialSnapshotRepository snapshotRepository) {
        this.projectRepository = projectRepository;
        this.projectPlanRepository = projectPlanRepository;
        this.planTaskRepository = planTaskRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.snapshotRepository = snapshotRepository;
    }

    // Runs every day at 02:00 AM
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void scheduledEvmRun() {
        log.info("Triggering scheduled EVM calculation");
        runEvmCalculationForDate(LocalDate.now());
    }

    @Transactional
    public void runEvmCalculationForDate(LocalDate today) {
        List<Project> activeProjects = projectRepository.findByStatusAndDeletedAtIsNull(ProjectStatus.ACTIVE);
        log.info("Running EVM calculations for date: {} across {} active projects", today, activeProjects.size());

        for (Project project : activeProjects) {
            try {
                calculateAndSaveSnapshot(project, today);
            } catch (Exception e) {
                log.error("Failed to calculate EVM snapshot for project code: " + project.getCode(), e);
            }
        }
    }

    @Transactional
    public void calculateAndSaveSnapshot(Project project, LocalDate today) {
        List<ProjectPlan> plans = projectPlanRepository.findByProjectIdAndDeletedAtIsNull(project.getId());
        Optional<ProjectPlan> activePlanOpt = plans.stream()
                .filter(p -> p.getStatus() == PlanStatus.ACTIVE)
                .findFirst();

        if (activePlanOpt.isEmpty()) {
            log.debug("No active plan found for project: {}", project.getCode());
            return;
        }

        ProjectPlan plan = activePlanOpt.get();
        List<PlanTask> tasks = planTaskRepository.findByPlanIdAndDeletedAtIsNull(plan.getId());

        double totalPv = 0.0;
        double totalEv = 0.0;
        double totalAc = 0.0;

        for (PlanTask task : tasks) {
            // EVM calculations are performed on leaf tasks only (not summaries or milestones)
            if (task.isSummary() || task.isMilestone()) {
                continue;
            }

            User owner = task.getOwner();
            double hourlyRate = 0.0;
            if (owner != null) {
                Optional<ProjectMember> memberOpt = projectMemberRepository.findByProjectIdAndUser_Id(project.getId(), owner.getId());
                if (memberOpt.isPresent() && memberOpt.get().getHourlyRate() != null) {
                    hourlyRate = memberOpt.get().getHourlyRate();
                }
            }

            double plannedEffortHours = (task.getPlannedEffortMinutes() == null) ? 0.0 : task.getPlannedEffortMinutes() / 60.0;
            double actualEffortHours = (task.getActualEffortMinutes() == null) ? 0.0 : task.getActualEffortMinutes() / 60.0;

            double bac = plannedEffortHours * hourlyRate; // Budget at Completion
            double progressRatio = calculatePlannedProgress(task.getPlannedStart(), task.getPlannedFinish(), today);

            totalPv += bac * progressRatio;
            totalEv += bac * (task.getPercentComplete() / 100.0);
            totalAc += actualEffortHours * hourlyRate;
        }

        double cv = totalEv - totalAc;
        double sv = totalEv - totalPv;
        double cpi = (totalAc > 0.0) ? (totalEv / totalAc) : 1.0;
        double spi = (totalPv > 0.0) ? (totalEv / totalPv) : 1.0;

        // Upsert snapshot for today
        ProjectFinancialSnapshot snapshot = snapshotRepository.findByProjectIdAndSnapshotDate(project.getId(), today)
                .orElse(new ProjectFinancialSnapshot());

        snapshot.setProject(project);
        snapshot.setSnapshotDate(today);
        snapshot.setPlannedValue(totalPv);
        snapshot.setEarnedValue(totalEv);
        snapshot.setActualCost(totalAc);
        snapshot.setCostVariance(cv);
        snapshot.setScheduleVariance(sv);
        snapshot.setCpi(cpi);
        snapshot.setSpi(spi);

        snapshotRepository.save(snapshot);
        log.info("Saved EVM snapshot for project: {} on {}. PV={}, EV={}, AC={}, CPI={}, SPI={}",
                project.getCode(), today, totalPv, totalEv, totalAc, cpi, spi);
    }

    private double calculatePlannedProgress(LocalDate start, LocalDate finish, LocalDate today) {
        if (start == null || finish == null) return 0.0;
        if (today.isBefore(start)) return 0.0;
        if (today.isAfter(finish)) return 1.0;
        long totalDays = java.time.temporal.ChronoUnit.DAYS.between(start, finish);
        if (totalDays <= 0) return 1.0;
        long elapsedDays = java.time.temporal.ChronoUnit.DAYS.between(start, today);
        return Math.min(1.0, (double) elapsedDays / totalDays);
    }
}
