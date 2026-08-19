package com.example.pmdaily.plan;

import java.util.UUID;

import com.example.pmdaily.common.BaseEntity;
import com.example.pmdaily.task.TimeUnit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "plan_template_tasks")
@Getter
@Setter
@NoArgsConstructor
public class PlanTemplateTask extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private PlanTemplate template;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "task_name", nullable = false)
    private String taskName;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 30)
    private PlanTaskType taskType = PlanTaskType.TASK;

    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo = 1;

    @Column(name = "wbs_code", length = 50)
    private String wbsCode;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes = 480;

    @Enumerated(EnumType.STRING)
    @Column(name = "duration_unit", nullable = false, length = 50, columnDefinition = "VARCHAR(50) DEFAULT 'MINUTE'")
    private TimeUnit durationUnit = TimeUnit.MINUTE;

    @Column(name = "planned_effort_minutes", nullable = false)
    private Integer plannedEffortMinutes = 480;

    @Enumerated(EnumType.STRING)
    @Column(name = "effort_unit", nullable = false, length = 50, columnDefinition = "VARCHAR(50) DEFAULT 'MINUTE'")
    private TimeUnit effortUnit = TimeUnit.MINUTE;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_mode", nullable = false, length = 20)
    private ScheduleMode scheduleMode = ScheduleMode.AUTO;
}
