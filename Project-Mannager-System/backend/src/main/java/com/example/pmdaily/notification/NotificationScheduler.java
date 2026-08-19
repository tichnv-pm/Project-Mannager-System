package com.example.pmdaily.notification;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.pmdaily.task.Task;
import com.example.pmdaily.task.TaskRepository;
import com.example.pmdaily.task.TaskStatus;

/**
 * Scheduled Job quét thông báo định kỳ (docs/api/11-notification-api.md muc 1 & 5).
 */
@Component
public class NotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);

    private final TaskRepository taskRepository;
    private final NotificationService notificationService;

    public NotificationScheduler(
            TaskRepository taskRepository,
            NotificationService notificationService) {
        this.taskRepository = taskRepository;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "0 0 1 * * ?")
    @Transactional
    public void scanNotifications() {
        log.info("notification.scheduler started");
        LocalDate today = LocalDate.now();

        Set<TaskStatus> activeStatuses = Set.of(
                TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED, TaskStatus.REVIEW);

        Specification<Task> spec = (root, query, cb) -> cb.and(
                cb.isNull(root.get("deletedAt")),
                root.get("status").in(activeStatuses),
                cb.isNotNull(root.get("assignee")),
                cb.isNotNull(root.get("dueDate"))
        );

        List<Task> activeTasks = taskRepository.findAll(spec);
        int createdCount = 0;

        for (Task task : activeTasks) {
            if (task.getAssignee() == null || task.getDueDate() == null) {
                continue;
            }

            LocalDate dueDate = task.getDueDate();
            if (dueDate.isBefore(today)) {
                var notif = notificationService.createNotificationInternal(
                        task.getAssignee(),
                        NotificationType.TASK_OVERDUE,
                        "Công việc đã quá hạn",
                        "Công việc " + task.getCode() + ": " + task.getTitle() + " đã quá hạn " + dueDate,
                        "TASK",
                        task.getId()
                );
                if (notif != null) createdCount++;
            } else if (!dueDate.isBefore(today) && dueDate.isBefore(today.plusDays(3))) {
                var notif = notificationService.createNotificationInternal(
                        task.getAssignee(),
                        NotificationType.TASK_DUE_SOON,
                        "Công việc sắp đến hạn",
                        "Công việc " + task.getCode() + ": " + task.getTitle() + " sẽ đến hạn vào " + dueDate,
                        "TASK",
                        task.getId()
                );
                if (notif != null) createdCount++;
            }
        }

        log.info("notification.scheduler finished createdCount={}", createdCount);
    }
}
