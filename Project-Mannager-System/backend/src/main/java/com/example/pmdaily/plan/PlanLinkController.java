package com.example.pmdaily.plan;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.pmdaily.plan.dto.LinkCreateRequest;
import com.example.pmdaily.plan.dto.LinkResponse;
import com.example.pmdaily.security.UserPrincipal;

import jakarta.validation.Valid;

/**
 * REST API plan links (docs/api/13-planning-api.md muc 2.8) — PLN-FR-LINK-*, PLN-RULE-LINK-*.
 */
@RestController
@RequestMapping("/api/v1")
public class PlanLinkController {

    private final PlanLinkService linkService;

    public PlanLinkController(PlanLinkService linkService) {
        this.linkService = linkService;
    }

    @PostMapping("/plans/{planId}/tasks/{taskId}/links")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('plan:link')")
    public LinkResponse create(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID planId,
            @PathVariable UUID taskId,
            @Valid @RequestBody LinkCreateRequest request) {
        return linkService.create(actor, planId, taskId, request);
    }

    @GetMapping("/plans/{planId}/tasks/{taskId}/links")
    @PreAuthorize("hasAuthority('plan:view')")
    public List<LinkResponse> list(@PathVariable UUID planId, @PathVariable UUID taskId) {
        return linkService.list(planId, taskId);
    }

    @DeleteMapping("/links/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('plan:link')")
    public void delete(@AuthenticationPrincipal UserPrincipal actor, @PathVariable UUID id) {
        linkService.delete(actor, id);
    }
}