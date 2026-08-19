package com.example.pmdaily.plan;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/**
 * Snapshot từng task của baseline (bảng plan_baseline_tasks) — docs/planning/11 muc 2, PLN-RULE-BASE-03.
 * Bảng chụp không có version nên không kế thừa BaseEntity.
 */
@Getter
@Setter
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "plan_baseline_tasks")
public class PlanBaselineTask {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "baseline_id", nullable = false)
    private PlanBaseline baseline;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private PlanTask task;

    @Column(name = "wbs_code", nullable = false, length = 60)
    private String wbsCode;

    @Column(name = "task_name", nullable = false, length = 200)
    private String taskName;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 30)
    private PlanTaskType taskType;

    @Column(name = "planned_start")
    private LocalDate plannedStart;

    @Column(name = "planned_finish")
    private LocalDate plannedFinish;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "planned_effort_minutes")
    private Integer plannedEffortMinutes;

    @Column(name = "percent_complete", nullable = false)
    private int percentComplete = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resources_snapshot")
    private String resourcesSnapshot;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;
}