package com.example.pmdaily.sprint.dto;

import java.time.LocalDate;
import java.util.UUID;
import com.example.pmdaily.sprint.SprintStatus;

public record SprintResponse(
    UUID id,
    UUID projectId,
    String sprintName,
    LocalDate startDate,
    LocalDate endDate,
    SprintStatus status,
    String goal
) {}
