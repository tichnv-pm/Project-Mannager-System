package com.example.pmdaily.plan.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.example.pmdaily.plan.ResourceType;

/**
 * Response gán/sửa allocation — kèm cảnh báo over-allocation sau khi ghi (PLN-RULE-RES-03).
 */
public record ResourceAssignmentResponse(
        UUID id,
        UUID planId,
        UUID taskId,
        String taskCode,
        String taskName,
        boolean taskSummary,
        ResourceType resourceType,
        UUID resourceId,
        String resourceName,
        String roleOnTask,
        int allocationPercent,
        LocalDate startDate,
        LocalDate endDate,
        Integer plannedEffortMinutes,
        boolean overAllocation,
        Double utilizationPercent) {
}