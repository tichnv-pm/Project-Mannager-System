package com.example.pmdaily.plan.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Tạo liên kết planning task -> entity ngoài (docs/api/13-planning-api.md muc 2.8).
 */
public record LinkCreateRequest(
        @NotBlank String targetType,
        @NotNull UUID targetId,
        @NotBlank String linkType,
        String note,
        Boolean isPrimaryExecution) {
}