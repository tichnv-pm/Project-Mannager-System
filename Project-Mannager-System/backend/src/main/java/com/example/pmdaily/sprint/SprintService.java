package com.example.pmdaily.sprint;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.pmdaily.common.ErrorCode;
import com.example.pmdaily.exception.BusinessException;
import com.example.pmdaily.exception.ConflictException;
import com.example.pmdaily.exception.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import com.example.pmdaily.project.ProjectMember;
import com.example.pmdaily.project.ProjectMemberRepository;
import com.example.pmdaily.project.ProjectMemberRole;
import com.example.pmdaily.security.UserPrincipal;
import com.example.pmdaily.sprint.dto.BurndownPointResponse;
import com.example.pmdaily.sprint.dto.SprintCreateRequest;
import com.example.pmdaily.sprint.dto.SprintResponse;
import com.example.pmdaily.sprint.dto.SprintUpdateRequest;
import com.example.pmdaily.sprint.dto.SprintVelocityResponse;
import com.example.pmdaily.task.Task;
import com.example.pmdaily.task.TaskRepository;
import com.example.pmdaily.task.TaskStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SprintService {

    private final SprintRepository sprintRepository;
    private final ProjectMemberRepository memberRepository;
    private final TaskRepository taskRepository;
    private final SprintBurndownSnapshotRepository burndownSnapshotRepository;

    private void checkProjectViewAccess(UserPrincipal actor, UUID projectId) {
        if (actor.getRoles().contains("ADMIN")) {
            return;
        }
        if (!memberRepository.existsByProjectIdAndUser_Id(projectId, actor.getId())) {
            throw new AccessDeniedException("Không có quyền truy cập dự án");
        }
    }

    private void checkProjectManageAccess(UserPrincipal actor, UUID projectId) {
        if (actor.getRoles().contains("ADMIN")) {
            return;
        }
        ProjectMember member = memberRepository.findByProjectIdAndUser_Id(projectId, actor.getId())
                .orElseThrow(() -> new AccessDeniedException("Không có quyền truy cập dự án"));
        if (member.getRole() != ProjectMemberRole.PROJECT_MANAGER) {
            throw new AccessDeniedException("Cần quyền PROJECT_MANAGER của dự án để thao tác");
        }
    }

    @Transactional(readOnly = true)
    public List<SprintResponse> getSprints(UUID projectId, UserPrincipal actor) {
        checkProjectViewAccess(actor, projectId);
        return sprintRepository.findByProjectIdAndDeletedAtIsNull(projectId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public SprintResponse createSprint(UUID projectId, SprintCreateRequest request, UserPrincipal actor) {
        checkProjectManageAccess(actor, projectId);

        if (request.startDate().isAfter(request.endDate())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Ngày bắt đầu không được lớn hơn ngày kết thúc");
        }

        Sprint sprint = new Sprint();
        sprint.setProjectId(projectId);
        sprint.setSprintName(request.sprintName().trim());
        sprint.setStartDate(request.startDate());
        sprint.setEndDate(request.endDate());
        sprint.setStatus(SprintStatus.FUTURE);
        sprint.setGoal(request.goal());
        sprint.setCreatedBy(actor.getId());
        sprint.setUpdatedBy(actor.getId());

        Sprint saved = sprintRepository.save(sprint);
        return toResponse(saved);
    }

    @Transactional
    public SprintResponse updateSprint(UUID sprintId, SprintUpdateRequest request, UserPrincipal actor) {
        Sprint sprint = sprintRepository.findByIdAndDeletedAtIsNull(sprintId)
                .orElseThrow(() -> new ResourceNotFoundException("Sprint", sprintId));
        checkProjectManageAccess(actor, sprint.getProjectId());

        if (request.startDate().isAfter(request.endDate())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Ngày bắt đầu không được lớn hơn ngày kết thúc");
        }

        // Active sprint validation (Only one active sprint per project)
        if (request.status() == SprintStatus.ACTIVE && sprint.getStatus() != SprintStatus.ACTIVE) {
            boolean hasActive = sprintRepository.existsByProjectIdAndStatusAndDeletedAtIsNull(
                    sprint.getProjectId(), SprintStatus.ACTIVE);
            if (hasActive) {
                throw new ConflictException("Đã có một Sprint đang hoạt động (ACTIVE) trong dự án");
            }
        }

        // Close Sprint workflow (Moving unfinished tasks to backlog)
        if (request.status() == SprintStatus.COMPLETED && sprint.getStatus() != SprintStatus.COMPLETED) {
            List<Task> tasks = taskRepository.findBySprintIdAndDeletedAtIsNull(sprintId);
            for (Task t : tasks) {
                if (t.getStatus() != TaskStatus.DONE) {
                    t.setSprintId(null);
                    taskRepository.save(t);
                }
            }
        }

        sprint.setSprintName(request.sprintName().trim());
        sprint.setStartDate(request.startDate());
        sprint.setEndDate(request.endDate());
        sprint.setStatus(request.status());
        sprint.setGoal(request.goal());
        sprint.setUpdatedBy(actor.getId());

        Sprint saved = sprintRepository.save(sprint);
        return toResponse(saved);
    }

    @Transactional
    public void deleteSprint(UUID sprintId, UserPrincipal actor) {
        Sprint sprint = sprintRepository.findByIdAndDeletedAtIsNull(sprintId)
                .orElseThrow(() -> new ResourceNotFoundException("Sprint", sprintId));
        checkProjectManageAccess(actor, sprint.getProjectId());

        if (sprint.getStatus() == SprintStatus.ACTIVE) {
            throw new ConflictException("Không thể xóa Sprint đang hoạt động (ACTIVE)");
        }

        // Clear sprint_id from tasks inside this sprint
        List<Task> tasks = taskRepository.findBySprintIdAndDeletedAtIsNull(sprintId);
        for (Task t : tasks) {
            t.setSprintId(null);
            taskRepository.save(t);
        }

        sprint.setDeletedAt(java.time.Instant.now());
        sprint.setDeletedBy(actor.getId());
        sprintRepository.save(sprint);
    }

    @Transactional(readOnly = true)
    public SprintVelocityResponse getVelocity(UUID sprintId, UserPrincipal actor) {
        Sprint sprint = sprintRepository.findByIdAndDeletedAtIsNull(sprintId)
                .orElseThrow(() -> new ResourceNotFoundException("Sprint", sprintId));
        checkProjectViewAccess(actor, sprint.getProjectId());
        return computeVelocity(sprint);
    }

    @Transactional(readOnly = true)
    public List<SprintVelocityResponse> getVelocityTrend(UUID projectId, UserPrincipal actor) {
        checkProjectViewAccess(actor, projectId);
        return sprintRepository.findByProjectIdAndDeletedAtIsNull(projectId).stream()
                .filter(s -> s.getStatus() == SprintStatus.COMPLETED)
                .sorted(java.util.Comparator.comparing(Sprint::getEndDate))
                .map(this::computeVelocity)
                .toList();
    }

    private SprintVelocityResponse computeVelocity(Sprint sprint) {
        List<Task> tasks = taskRepository.findBySprintIdAndDeletedAtIsNull(sprint.getId());
        int total = 0;
        int completed = 0;
        int completedTasks = 0;
        for (Task task : tasks) {
            int points = task.getStoryPoints() != null ? task.getStoryPoints() : 0;
            total += points;
            if (task.getStatus() == TaskStatus.DONE) {
                completed += points;
                completedTasks++;
            }
        }
        return new SprintVelocityResponse(sprint.getId(), sprint.getSprintName(),
                total, completed, tasks.size(), completedTasks);
    }

    @Transactional(readOnly = true)
    public List<BurndownPointResponse> getBurndown(UUID sprintId, UserPrincipal actor) {
        Sprint sprint = sprintRepository.findByIdAndDeletedAtIsNull(sprintId)
                .orElseThrow(() -> new ResourceNotFoundException("Sprint", sprintId));
        checkProjectViewAccess(actor, sprint.getProjectId());

        List<SprintBurndownSnapshot> snapshots =
                burndownSnapshotRepository.findBySprintIdOrderBySnapshotDateAsc(sprintId);
        if (snapshots.isEmpty()) {
            return List.of();
        }

        int baselineTotal = snapshots.get(0).getTotalStoryPoints();
        long totalSprintDays = Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(
                sprint.getStartDate(), sprint.getEndDate()));

        return snapshots.stream()
                .map(s -> {
                    long elapsedDays = java.time.temporal.ChronoUnit.DAYS.between(
                            sprint.getStartDate(), s.getSnapshotDate());
                    double fractionElapsed = Math.min(1.0, Math.max(0.0, (double) elapsedDays / totalSprintDays));
                    double idealRemaining = baselineTotal * (1.0 - fractionElapsed);
                    return new BurndownPointResponse(s.getSnapshotDate(), s.getTotalStoryPoints(),
                            s.getCompletedStoryPoints(), s.getRemainingStoryPoints(), idealRemaining);
                })
                .toList();
    }

    private SprintResponse toResponse(Sprint s) {
        return new SprintResponse(
                s.getId(),
                s.getProjectId(),
                s.getSprintName(),
                s.getStartDate(),
                s.getEndDate(),
                s.getStatus(),
                s.getGoal()
        );
    }
}
