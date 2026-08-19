package com.example.pmdaily.plan.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.example.pmdaily.plan.PlanType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreatePlanFromTemplateRequestDto {

    @NotBlank
    private String projectId;

    @NotNull
    private UUID templateId;

    @NotBlank
    private String planCode;

    @NotBlank
    private String planName;

    private PlanType planType = PlanType.MASTER;

    private String parentPlanId;

    @NotNull
    private LocalDate startDate;
}
