package com.example.pmdaily.plan.dto;

import java.time.LocalDate;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PortfolioMilestoneDto {
    private UUID id;
    private String name;
    private LocalDate targetDate;
    private String status;
    private String projectId;
    private String projectName;
}
