package com.example.pmdaily.plan.dto;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PortfolioSummaryDto {
    private Integer totalProjects;
    private Integer activeProjects;
    private Integer delayedProjects;
    private Integer overAllocatedResourcesCount;
    private Double averageProgress;
    private List<PortfolioProjectDto> projects;
    private List<PortfolioMilestoneDto> upcomingMilestones;
}
