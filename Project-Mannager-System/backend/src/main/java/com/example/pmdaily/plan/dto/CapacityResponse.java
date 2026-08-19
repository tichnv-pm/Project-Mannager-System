package com.example.pmdaily.plan.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.example.pmdaily.plan.CapacitySource;
import com.example.pmdaily.plan.ResourceType;

public record CapacityResponse(
        UUID id,
        ResourceType resourceType,
        UUID resourceId,
        int capacityPercent,
        LocalDate startDate,
        LocalDate endDate,
        CapacitySource source) {
}