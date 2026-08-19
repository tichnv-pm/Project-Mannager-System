package com.example.pmdaily.plan.dto;

import java.util.UUID;

import com.example.pmdaily.plan.PlanTaskType;
import com.example.pmdaily.plan.ScheduleMode;

import lombok.Data;

@Data
public class PlanTemplateTaskResponseDto {
    private UUID id;
    private UUID parentId;
    private String taskName;
    private PlanTaskType taskType;
    private Integer sequenceNo;
    private String wbsCode;
    private Integer durationMinutes;
    private com.example.pmdaily.task.TimeUnit durationUnit;
    private Integer plannedEffortMinutes;
    private com.example.pmdaily.task.TimeUnit effortUnit;
    private ScheduleMode scheduleMode;
}
