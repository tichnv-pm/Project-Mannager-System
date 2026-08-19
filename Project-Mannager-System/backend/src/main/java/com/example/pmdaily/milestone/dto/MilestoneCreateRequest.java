package com.example.pmdaily.milestone.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MilestoneCreateRequest(
        @NotNull(message = "projectId is required")
        UUID projectId,

        @NotBlank(message = "name is required")
        @Size(max = 150, message = "name max 150 characters")
        String name,

        String description,

        @NotNull(message = "plannedDate is required")
        LocalDate plannedDate,

        String note
) {}
