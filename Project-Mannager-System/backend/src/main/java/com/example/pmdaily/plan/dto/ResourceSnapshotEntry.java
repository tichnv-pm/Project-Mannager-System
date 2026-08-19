package com.example.pmdaily.plan.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.example.pmdaily.plan.PlanTaskResource;
import com.example.pmdaily.plan.ResourceType;

/**
 * Danh sách resource của 1 task chụp trong snapshot (version/baseline).
 */
public record ResourceSnapshotEntry(
        ResourceType resourceType,
        UUID resourceId,
        String roleOnTask,
        int allocationPercent,
        LocalDate startDate,
        LocalDate endDate) {

    public static List<ResourceSnapshotEntry> list(List<PlanTaskResource> rows) {
        return rows.stream()
                .map(r -> new ResourceSnapshotEntry(r.getResourceType(), r.getResourceId(),
                        r.getRoleOnTask(), r.getAllocationPercent(), r.getStartDate(), r.getEndDate()))
                .toList();
    }
}