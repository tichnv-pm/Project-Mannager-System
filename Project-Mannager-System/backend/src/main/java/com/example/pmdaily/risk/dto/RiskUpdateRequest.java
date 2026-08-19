package com.example.pmdaily.risk.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.example.pmdaily.risk.RiskImpact;
import com.example.pmdaily.risk.RiskLevel;
import com.example.pmdaily.risk.RiskProbability;
import com.example.pmdaily.risk.RiskStatus;

public record RiskUpdateRequest(
        @NotBlank(message = "title is required")
        @Size(max = 200, message = "title max 200 characters")
        String title,

        String description,

        @NotNull(message = "probability is required")
        RiskProbability probability,

        @NotNull(message = "impact is required")
        RiskImpact impact,

        RiskLevel level,

        @NotNull(message = "ownerId is required")
        UUID ownerId,

        String mitigationPlan,

        String contingencyPlan,

        @NotNull(message = "status is required")
        RiskStatus status,

        LocalDate dueDate,

        @NotNull(message = "version is required")
        Long version
) {}
