package com.example.pmdaily.milestone.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.example.pmdaily.milestone.MilestoneStatus;

public record MilestoneResponse(
        UUID id,
        UUID projectId,
        String projectCode,
        String projectName,
        String name,
        String description,
        LocalDate plannedDate,
        LocalDate actualDate,
        MilestoneStatus status,
        int progress,
        String note,
        Instant createdAt,
        long version
) {}
