package com.example.pmdaily.milestone;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.pmdaily.common.PageResponse;
import com.example.pmdaily.milestone.dto.MilestoneCreateRequest;
import com.example.pmdaily.milestone.dto.MilestoneResponse;
import com.example.pmdaily.milestone.dto.MilestoneUpdateRequest;
import com.example.pmdaily.security.UserPrincipal;

import jakarta.validation.Valid;

/**
 * REST API Milestone (docs/api/10-milestone-api.md) — 5 endpoints.
 */
@RestController
@RequestMapping("/api/v1/milestones")
public class MilestoneController {

    private final MilestoneService milestoneService;

    public MilestoneController(MilestoneService milestoneService) {
        this.milestoneService = milestoneService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('milestone:view')")
    public PageResponse<MilestoneResponse> list(
            @AuthenticationPrincipal UserPrincipal actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) MilestoneStatus status) {
        return milestoneService.search(actor, keyword, projectId, status, page, size, sort);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('milestone:manage')")
    public MilestoneResponse create(
            @AuthenticationPrincipal UserPrincipal actor,
            @Valid @RequestBody MilestoneCreateRequest request) {
        return milestoneService.create(actor, request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('milestone:view')")
    public MilestoneResponse get(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id) {
        return milestoneService.get(id, actor);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('milestone:manage')")
    public MilestoneResponse update(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id,
            @Valid @RequestBody MilestoneUpdateRequest request) {
        return milestoneService.update(actor, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('milestone:manage')")
    public void delete(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id) {
        milestoneService.delete(actor, id);
    }
}
