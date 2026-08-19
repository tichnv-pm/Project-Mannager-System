package com.example.pmdaily.plan;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.pmdaily.plan.dto.ChangeHistoryResponse;
import com.example.pmdaily.plan.dto.ChangeSuggestionCreateRequest;
import com.example.pmdaily.plan.dto.ChangeSuggestionResponse;
import com.example.pmdaily.security.UserPrincipal;

import jakarta.validation.Valid;

/**
 * REST API change history & suggestion (docs/api/13-planning-api.md muc 2.9) — PLN-FR-CHG-*.
 */
@RestController
@RequestMapping("/api/v1")
public class PlanChangeController {

    private final PlanChangeService changeService;

    public PlanChangeController(PlanChangeService changeService) {
        this.changeService = changeService;
    }

    @GetMapping("/plans/{planId}/change-histories")
    @PreAuthorize("hasAuthority('plan:view')")
    public List<ChangeHistoryResponse> histories(@PathVariable UUID planId) {
        return changeService.listHistories(planId);
    }

    @GetMapping("/plans/{planId}/change-suggestions")
    @PreAuthorize("hasAuthority('plan:view')")
    public List<ChangeSuggestionResponse> suggestions(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID planId) {
        return changeService.listSuggestions(actor, planId);
    }

    @PostMapping("/plans/{planId}/change-suggestions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('plan:change')")
    public ChangeSuggestionResponse createSuggestion(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID planId,
            @Valid @RequestBody ChangeSuggestionCreateRequest request) {
        return changeService.createSuggestion(actor, planId, request);
    }

    @PostMapping("/change-suggestions/{id}/accept")
    @PreAuthorize("hasAuthority('plan:change')")
    public ChangeSuggestionResponse accept(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id) {
        return changeService.accept(actor, id);
    }

    @PostMapping("/change-suggestions/{id}/reject")
    @PreAuthorize("hasAuthority('plan:change')")
    public ChangeSuggestionResponse reject(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id) {
        return changeService.reject(actor, id);
    }
}