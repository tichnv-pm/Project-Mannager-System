package com.example.pmdaily.project.dto;

import java.time.LocalDate;
import java.util.UUID;

public record EvmSnapshotResponse(
    UUID id,
    LocalDate snapshotDate,
    Double plannedValue,
    Double earnedValue,
    Double actualCost,
    Double costVariance,
    Double scheduleVariance,
    Double cpi,
    Double spi
) {}
