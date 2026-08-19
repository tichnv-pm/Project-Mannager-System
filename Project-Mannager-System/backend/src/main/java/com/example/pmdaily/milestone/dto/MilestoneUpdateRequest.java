package com.example.pmdaily.milestone.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.example.pmdaily.milestone.MilestoneStatus;

public record MilestoneUpdateRequest(
        @NotBlank(message = "name is required")
        @Size(max = 150, message = "name max 150 characters")
        String name,

        String description,

        @NotNull(message = "plannedDate is required")
        LocalDate plannedDate,

        @NotNull(message = "status is required")
        MilestoneStatus status,

        @Min(value = 0, message = "progress must be >= 0")
        @Max(value = 100, message = "progress must be <= 100")
        int progress,

        LocalDate actualDate,

        String note,

        @NotNull(message = "version is required")
        Long version
) {}
