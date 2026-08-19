package com.example.pmdaily.plan;

import java.time.LocalDate;
import java.util.List;
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

import com.example.pmdaily.plan.dto.CapacityResponse;
import com.example.pmdaily.plan.dto.ResourceAssignmentRequest;
import com.example.pmdaily.plan.dto.ResourceAssignmentResponse;
import com.example.pmdaily.plan.dto.ResourceAssignmentUpdateRequest;
import com.example.pmdaily.plan.dto.ResourceOverviewRow;
import com.example.pmdaily.plan.dto.WorkloadResponse;
import com.example.pmdaily.security.UserPrincipal;

import jakarta.validation.Valid;

/**
 * REST API Resource Planning & Workload (docs/api/13-planning-api.md muc 2.6) — PLN-FR-RES-*.
 */
@RestController
@RequestMapping("/api/v1")
public class PlanResourceController {

    private final PlanResourceService resourceService;

    public PlanResourceController(PlanResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @PostMapping("/plans/{planId}/tasks/{taskId}/resources")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('plan:resource')")
    public ResourceAssignmentResponse assign(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID planId,
            @PathVariable UUID taskId,
            @Valid @RequestBody ResourceAssignmentRequest request) {
        return resourceService.assign(actor, planId, taskId, request);
    }

    @PutMapping("/resource-allocations/{id}")
    @PreAuthorize("hasAuthority('plan:resource')")
    public ResourceAssignmentResponse update(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id,
            @Valid @RequestBody ResourceAssignmentUpdateRequest request) {
        return resourceService.update(actor, id, request);
    }

    @DeleteMapping("/resource-allocations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('plan:resource')")
    public void remove(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id) {
        resourceService.remove(actor, id);
    }

    @PutMapping("/resources/{resourceId}/capacity")
    @PreAuthorize("hasAuthority('plan:resource')")
    public CapacityResponse upsertCapacity(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID resourceId,
            @Valid @RequestBody com.example.pmdaily.plan.dto.CapacityUpdateRequest request) {
        return resourceService.upsertCapacity(actor.getId(), resourceId, request);
    }

    @GetMapping("/resources/{resourceId}/workload")
    @PreAuthorize("hasAuthority('plan:view')")
    public WorkloadResponse workload(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID resourceId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(defaultValue = "DAY") WorkloadGranularity granularity) {
        return resourceService.workload(actor, resourceId, from, to, granularity);
    }

    @GetMapping("/plans/{planId}/workload")
    @PreAuthorize("hasAuthority('plan:view')")
    public List<WorkloadResponse> planWorkload(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID planId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(defaultValue = "DAY") WorkloadGranularity granularity) {
        return resourceService.planWorkload(actor, planId, from, to, granularity);
    }

    @GetMapping("/plans/{planId}/resources")
    @PreAuthorize("hasAuthority('plan:view')")
    public List<ResourceAssignmentResponse> listPlanResources(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID planId) {
        return resourceService.listPlanResources(actor, planId);
    }

    @GetMapping("/resources/overview")
    @PreAuthorize("hasAuthority('plan:view')")
    public List<ResourceOverviewRow> overview(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        return resourceService.overview(from, to);
    }
}