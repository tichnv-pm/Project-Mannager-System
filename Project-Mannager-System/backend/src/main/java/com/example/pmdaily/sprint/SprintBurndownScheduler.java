package com.example.pmdaily.sprint;

import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.pmdaily.task.Task;
import com.example.pmdaily.task.TaskRepository;
import com.example.pmdaily.task.TaskStatus;

/**
 * Ghi snapshot remaining/completed story points mỗi ngày cho các sprint ACTIVE, dùng vẽ Burndown
 * (cùng mẫu với {@link com.example.pmdaily.project.EvmScheduler}).
 */
@Service
public class SprintBurndownScheduler {

    private static final Logger log = LoggerFactory.getLogger(SprintBurndownScheduler.class);

    private final SprintRepository sprintRepository;
    private final TaskRepository taskRepository;
    private final SprintBurndownSnapshotRepository snapshotRepository;

    public SprintBurndownScheduler(SprintRepository sprintRepository, TaskRepository taskRepository,
            SprintBurndownSnapshotRepository snapshotRepository) {
        this.sprintRepository = sprintRepository;
        this.taskRepository = taskRepository;
        this.snapshotRepository = snapshotRepository;
    }

    // Chạy mỗi ngày lúc 02:15 (sau EVM 02:00)
    @Scheduled(cron = "0 15 2 * * *")
    @Transactional
    public void scheduledBurndownRun() {
        log.info("Triggering scheduled sprint burndown snapshot");
        runBurndownSnapshotForDate(LocalDate.now());
    }

    @Transactional
    public void runBurndownSnapshotForDate(LocalDate today) {
        List<Sprint> activeSprints = sprintRepository.findByStatusAndDeletedAtIsNull(SprintStatus.ACTIVE);
        log.info("Running burndown snapshot for date: {} across {} active sprints", today, activeSprints.size());
        for (Sprint sprint : activeSprints) {
            try {
                calculateAndSaveSnapshot(sprint, today);
            } catch (Exception e) {
                log.error("Failed to calculate burndown snapshot for sprint id=" + sprint.getId(), e);
            }
        }
    }

    @Transactional
    public SprintBurndownSnapshot calculateAndSaveSnapshot(Sprint sprint, LocalDate today) {
        List<Task> tasks = taskRepository.findBySprintIdAndDeletedAtIsNull(sprint.getId());

        int total = 0;
        int completed = 0;
        for (Task task : tasks) {
            int points = task.getStoryPoints() != null ? task.getStoryPoints() : 0;
            total += points;
            if (task.getStatus() == TaskStatus.DONE) {
                completed += points;
            }
        }
        int remaining = total - completed;

        SprintBurndownSnapshot snapshot = snapshotRepository
                .findBySprintIdAndSnapshotDate(sprint.getId(), today)
                .orElse(new SprintBurndownSnapshot());
        snapshot.setSprintId(sprint.getId());
        snapshot.setSnapshotDate(today);
        snapshot.setTotalStoryPoints(total);
        snapshot.setCompletedStoryPoints(completed);
        snapshot.setRemainingStoryPoints(remaining);

        SprintBurndownSnapshot saved = snapshotRepository.save(snapshot);
        log.info("Saved burndown snapshot sprintId={} date={} total={} completed={} remaining={}",
                sprint.getId(), today, total, completed, remaining);
        return saved;
    }
}
