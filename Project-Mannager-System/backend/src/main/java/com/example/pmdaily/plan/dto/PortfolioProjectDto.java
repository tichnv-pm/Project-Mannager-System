package com.example.pmdaily.plan.dto;

import java.time.LocalDate;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PortfolioProjectDto {
    private String id;
    private String code;
    private String name;
    private String pmName;
    private String status;
    private LocalDate plannedStart;
    private LocalDate plannedFinish;
    private Double progress;
    private Integer delayDays;
    private Boolean isOverAllocated;
    private Integer criticalTaskCount;
    private UUID activePlanId;
}
