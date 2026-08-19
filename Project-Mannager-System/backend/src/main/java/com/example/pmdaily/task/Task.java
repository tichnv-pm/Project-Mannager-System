package com.example.pmdaily.task;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

import com.example.pmdaily.common.SoftDeleteEntity;
import com.example.pmdaily.project.Project;
import com.example.pmdaily.user.User;

/**
 * Công việc (bảng tasks) — docs/api/05-task-api.md, BR-TASK.
 * Mã tự sinh PRJXXX-TASK-000001 (BR-TASK-14); soft delete (BR-TASK-09).
 */
@Getter
@Setter
@Entity
@Table(name = "tasks")
public class Task extends SoftDeleteEntity {

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_task_id")
    private Task parentTask;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TaskStatus status = TaskStatus.TODO;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 10)
    private TaskPriority priority = TaskPriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private TaskType type = TaskType.TASK;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 20)
    private TaskSource source = TaskSource.MANUAL;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "actual_completed_at")
    private Instant actualCompletedAt;

    @Column(name = "progress", nullable = false)
    private int progress;

    @Column(name = "blocked", nullable = false)
    private boolean blocked;

    @Column(name = "blocker_reason", length = 500)
    private String blockerReason;

    @Column(name = "estimate_minutes")
    private Integer estimateMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "estimate_unit", nullable = false, length = 50, columnDefinition = "VARCHAR(50) DEFAULT 'MINUTE'")
    private TimeUnit estimateUnit = TimeUnit.MINUTE;

    @Column(name = "actual_minutes")
    private Integer actualMinutes;

    @Column(name = "notes")
    private String notes;

    @Column(name = "sprint_id")
    private UUID sprintId;
}
