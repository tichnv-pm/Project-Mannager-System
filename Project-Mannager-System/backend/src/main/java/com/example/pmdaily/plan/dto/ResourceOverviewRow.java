package com.example.pmdaily.plan.dto;

import com.example.pmdaily.plan.ResourceType;

/**
 * Dòng tổng hợp over-allocation cross-plan (docs/api/13-planning-api.md muc 2.6, docs/planning/10 muc 4-5).
 */
public record ResourceOverviewRow(
        ResourceType resourceType,
        java.util.UUID resourceId,
        String resourceName,
        long demandMinutes,
        Integer capacityMinutes,
        Double utilizationPercent,
        boolean overAllocation) {
}