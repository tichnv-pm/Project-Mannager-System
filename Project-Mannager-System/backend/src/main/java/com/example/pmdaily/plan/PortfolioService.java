package com.example.pmdaily.plan;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.pmdaily.plan.dto.PortfolioMilestoneDto;
import com.example.pmdaily.plan.dto.PortfolioProjectDto;
import com.example.pmdaily.plan.dto.PortfolioSummaryDto;
import com.example.pmdaily.project.Project;
import com.example.pmdaily.project.ProjectRepository;
import com.example.pmdaily.security.UserPrincipal;
import com.example.pmdaily.user.User;
import com.example.pmdaily.user.UserRepository;

@Service
@Transactional(readOnly = true)
public class PortfolioService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioService.class);

    private final ProjectRepository projectRepository;
    private final ProjectPlanRepository planRepository;
    private final PlanTaskRepository taskRepository;
    private final UserRepository userRepository;

    public PortfolioService(
            ProjectRepository projectRepository,
            ProjectPlanRepository planRepository,
            PlanTaskRepository taskRepository,
            UserRepository userRepository) {
        this.projectRepository = projectRepository;
        this.planRepository = planRepository;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
    }

    public PortfolioSummaryDto getPortfolioSummary(
            String pmId,
            String statusFilter,
            String search,
            LocalDate fromDate,
            LocalDate toDate,
            UserPrincipal currentUser) {

        List<Project> projects = projectRepository.findAll();

        if (search != null && !search.isBlank()) {
            String lowerSearch = search.toLowerCase();
            projects = projects.stream()
                    .filter(p -> p.getName().toLowerCase().contains(lowerSearch) || p.getCode().toLowerCase().contains(lowerSearch))
                    .collect(Collectors.toList());
        }

        List<PortfolioProjectDto> portfolioProjects = new ArrayList<>();
        List<PortfolioMilestoneDto> upcomingMilestones = new ArrayList<>();

        int delayedProjectsCount = 0;
        int overAllocatedCount = 0;
        double totalProgressSum = 0.0;
        int activeProjectsCount = 0;
        LocalDate today = LocalDate.now();

        for (Project prj : projects) {
            UUID prjId = prj.getId();
            List<ProjectPlan> plans = planRepository.findByProjectIdAndDeletedAtIsNull(prj.getId());

            ProjectPlan activeMaster = plans.stream()
                    .filter(p -> p.getPlanType() == PlanType.MASTER && (p.getStatus() == PlanStatus.ACTIVE || p.getStatus() == PlanStatus.APPROVED))
                    .findFirst()
                    .orElse(null);

            String pmName = "Unassigned";
            if (prj.getCreatedBy() != null) {
                pmName = userRepository.findById(prj.getCreatedBy())
                        .map(User::getFullName)
                        .orElse("Unassigned");
            }

            LocalDate pStart = prj.getStartDate();
            LocalDate pFinish = prj.getEndDate();
            Double progress = 0.0;
            Integer delayDays = 0;
            Boolean isOverAllocated = false;
            Integer criticalTaskCount = 0;
            UUID activePlanId = null;

            if (activeMaster != null) {
                activePlanId = activeMaster.getId();
                if (activeMaster.getPlannedStart() != null) pStart = activeMaster.getPlannedStart();
                if (activeMaster.getPlannedFinish() != null) pFinish = activeMaster.getPlannedFinish();

                List<PlanTask> tasks = taskRepository.findByPlanIdAndDeletedAtIsNull(activeMaster.getId());

                double totalEffort = tasks.stream().mapToDouble(t -> t.getPlannedEffortMinutes() != null ? t.getPlannedEffortMinutes() : 0).sum();
                if (totalEffort > 0) {
                    double doneEffort = tasks.stream().mapToDouble(t -> (t.getPlannedEffortMinutes() != null ? t.getPlannedEffortMinutes() : 0) * t.getPercentComplete() / 100.0).sum();
                    progress = Math.round((doneEffort / totalEffort) * 100.0 * 100.0) / 100.0;
                } else if (!tasks.isEmpty()) {
                    progress = Math.round(tasks.stream().mapToInt(PlanTask::getPercentComplete).average().orElse(0.0) * 100.0) / 100.0;
                }

                if (pFinish != null && pFinish.isBefore(today) && progress < 100.0) {
                    delayDays = (int) ChronoUnit.DAYS.between(pFinish, today);
                }

                criticalTaskCount = (int) tasks.stream().filter(PlanTask::isCritical).count();

                // Collect milestones
                tasks.stream()
                        .filter(t -> t.getTaskType() == PlanTaskType.MILESTONE)
                        .forEach(m -> upcomingMilestones.add(PortfolioMilestoneDto.builder()
                                .id(m.getId())
                                .name(m.getTaskName())
                                .targetDate(m.getPlannedFinish() != null ? m.getPlannedFinish() : m.getPlannedStart())
                                .status(m.getPercentComplete() >= 100 ? "COMPLETED" : "PLANNED")
                                .projectId(prjId.toString())
                                .projectName(prj.getName())
                                .build()));
            }

            if (delayDays > 0) delayedProjectsCount++;
            if (activeMaster != null && activeMaster.getStatus() == PlanStatus.ACTIVE) activeProjectsCount++;
            totalProgressSum += progress;

            portfolioProjects.add(PortfolioProjectDto.builder()
                    .id(prjId.toString())
                    .code(prj.getCode())
                    .name(prj.getName())
                    .pmName(pmName)
                    .status(activeMaster != null ? activeMaster.getStatus().name() : "DRAFT")
                    .plannedStart(pStart)
                    .plannedFinish(pFinish)
                    .progress(progress)
                    .delayDays(delayDays)
                    .isOverAllocated(isOverAllocated)
                    .criticalTaskCount(criticalTaskCount)
                    .activePlanId(activePlanId)
                    .build());
        }

        double avgProgress = portfolioProjects.isEmpty() ? 0.0 : Math.round((totalProgressSum / portfolioProjects.size()) * 100.0) / 100.0;

        return PortfolioSummaryDto.builder()
                .totalProjects(portfolioProjects.size())
                .activeProjects(activeProjectsCount)
                .delayedProjects(delayedProjectsCount)
                .overAllocatedResourcesCount(overAllocatedCount)
                .averageProgress(avgProgress)
                .projects(portfolioProjects)
                .upcomingMilestones(upcomingMilestones)
                .build();
    }
}
