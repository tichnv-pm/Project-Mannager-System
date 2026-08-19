package com.example.pmdaily.plan.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Tạo change suggestion (docs/api/13-planning-api.md muc 2.9) — PLN-AC-CHG-02/04.
 */
public record ChangeSuggestionCreateRequest(
        String sourceType,
        UUID sourceId,
        @NotBlank String title,
        @NotBlank String description,
        @NotNull @Valid List<SuggestionChangeField> suggestedChanges) {
}