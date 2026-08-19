package com.example.pmdaily.plan.dto;

import java.util.UUID;

/**
 * Một thay đổi đề xuất trong change suggestion — áp dụng lên PLAN_TASK.
 */
public record SuggestionChangeField(
        String entityType,
        UUID entityId,
        String field,
        String oldValue,
        String newValue) {
}