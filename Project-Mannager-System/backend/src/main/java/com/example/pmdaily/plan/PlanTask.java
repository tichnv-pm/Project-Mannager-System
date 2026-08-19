package com.example.pmdaily.plan;

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
import com.example.pmdaily.user.User;
import com.example.pmdaily.task.TimeUnit;

/**
 * Planning task — node của cây WBS (bảng plan_tasks) — PLN-FR-WBS-*.
 * docs/database/02 muc 27, docs/planning/07.
 */
@Getter
@Setter
@Entity
@Table(name = "plan_tasks")
public class PlanTask extends SoftDeleteEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private ProjectPlan plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private PlanTask parent;

    @Column(name = "wbs_code", nullable = false, length = 60)
    private String wbsCode;

    @Column(name = "task_code", nullable = false, length = 40)
    private String taskCode;

    @Column(name = "task_name", nullable = false, length = 200)
    private String taskName;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 30)
    private PlanTaskType taskType;

    @Column(name = "outline_level", nullable = false)
    private int outlineLevel = 1;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber = 1;

    @Column(name = "phase", length = 50)
    private String phase;

    @Column(name = "work_package", length = 50)
    private String workPackage;

    @Column(name = "deliverable", length = 200)
    private String deliverable;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Column(name = "planned_start")
    private LocalDate plannedStart;

    @Column(name = "planned_finish")
    private LocalDate plannedFinish;

    @Column(name = "duration_minutes")
    private Long durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "duration_unit", nullable = false, length = 50, columnDefinition = "VARCHAR(50) DEFAULT 'MINUTE'")
    private TimeUnit durationUnit = TimeUnit.MINUTE;

    @Column(name = "planned_effort_minutes")
    private Integer plannedEffortMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "effort_unit", nullable = false, length = 50, columnDefinition = "VARCHAR(50) DEFAULT 'MINUTE'")
    private TimeUnit effortUnit = TimeUnit.MINUTE;

    @Column(name = "actual_start")
    private LocalDate actualStart;

    @Column(name = "actual_finish")
    private LocalDate actualFinish;

    @Column(name = "actual_effort_minutes")
    private Integer actualEffortMinutes;

    @Column(name = "remaining_effort_minutes")
    private Integer remainingEffortMinutes;

    @Column(name = "percent_complete", nullable = false)
    private int percentComplete = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PlanTaskStatus status = PlanTaskStatus.NOT_STARTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 10)
    private TaskPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_mode", nullable = false, length = 10)
    private ScheduleMode scheduleMode = ScheduleMode.AUTO;

    @Enumerated(EnumType.STRING)
    @Column(name = "constraint_type", length = 30)
    private ConstraintType constraintType;

    @Column(name = "constraint_date")
    private LocalDate constraintDate;

    @Column(name = "is_summary", nullable = false)
    private boolean isSummary = false;

    @Column(name = "is_milestone", nullable = false)
    private boolean isMilestone = false;

    @Column(name = "is_critical", nullable = false)
    private boolean isCritical = false;
}
