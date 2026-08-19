package com.example.pmdaily.actionitem;

import java.time.LocalDate;

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
import com.example.pmdaily.meeting.Meeting;
import com.example.pmdaily.project.Project;
import com.example.pmdaily.task.Task;
import com.example.pmdaily.task.TaskPriority;
import com.example.pmdaily.user.User;

/**
 * Action item — việc cần làm từ cuộc họp (docs/api/07-action-item-api.md, FR-AI-01..04, BR-AI-01..04).
 * Cùng project với meeting (BR-AI-01); assignee thuộc project (BR-AI-02);
 * tối đa 1 task liên kết (BR-AI-03/04 — uk_action_items_linked_task).
 */
@Getter
@Setter
@Entity
@Table(name = "action_items")
public class ActionItem extends SoftDeleteEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id", nullable = false)
    private User assignee;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 10)
    private TaskPriority priority = TaskPriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ActionItemStatus status = ActionItemStatus.OPEN;

    @Column(name = "progress", nullable = false)
    private int progress;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_task_id")
    private Task linkedTask;
}
