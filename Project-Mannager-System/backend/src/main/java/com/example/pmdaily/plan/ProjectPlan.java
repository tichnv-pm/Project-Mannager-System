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
import com.example.pmdaily.project.Project;

/**
 * Kế hoạch dự án (bảng project_plans) — phân hệ PROJECT PLANNING v1.1.
 * docs/database/02 muc 25, docs/api/13-planning-api.md muc 2.1, PLN-FR-PLAN-*.
 */
@Getter
@Setter
@Entity
@Table(name = "project_plans")
public class ProjectPlan extends SoftDeleteEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "plan_code", nullable = false, length = 50)
    private String planCode;

    @Column(name = "plan_name", nullable = false, length = 200)
    private String planName;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false, length = 30)
    private PlanType planType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_plan_id")
    private ProjectPlan parentPlan;

    @Column(name = "calendar_id")
    private java.util.UUID calendarId;

    @Column(name = "parent_milestone_task_id")
    private java.util.UUID parentMilestoneTaskId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "active_version_id")
    private PlanVersion activeVersion;

    @Column(name = "planned_start")
    private LocalDate plannedStart;

    @Column(name = "planned_finish")
    private LocalDate plannedFinish;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PlanStatus status = PlanStatus.DRAFT;

    @Column(name = "progress", nullable = false)
    private int progress = 0;

    @Column(name = "duration_minutes")
    private Long durationMinutes;

    @Column(name = "note")
    private String note;
}
