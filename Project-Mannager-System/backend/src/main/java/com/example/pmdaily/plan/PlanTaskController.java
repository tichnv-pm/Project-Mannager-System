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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.pmdaily.plan.dto.DependencyCreateRequest;
import com.example.pmdaily.plan.dto.DependencyResponse;
import com.example.pmdaily.plan.dto.PlanTaskCreateRequest;
import com.example.pmdaily.plan.dto.PlanTaskMoveRequest;
import com.example.pmdaily.plan.dto.PlanTaskResponse;
import com.example.pmdaily.plan.dto.PlanTaskUpdateRequest;
import com.example.pmdaily.security.UserPrincipal;

import jakarta.validation.Valid;

/**
 * REST API WBS / Planning Task (docs/api/13-planning-api.md muc 2.2) — PLN-FR-WBS-*.
 */
@RestController
@RequestMapping("/api/v1/plans/{planId}/tasks")
public class PlanTaskController {

    private final PlanTaskService planTaskService;
    private final PlanTaskDependencyService planTaskDependencyService;

    public PlanTaskController(PlanTaskService planTaskService,
            PlanTaskDependencyService planTaskDependencyService) {
        this.planTaskService = planTaskService;
        this.planTaskDependencyService = planTaskDependencyService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('plan:view')")
    public List<PlanTaskResponse> tree(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID planId) {
        return planTaskService.tree(planId, actor);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('plan:update')")
    public PlanTaskResponse create(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID planId,
            @Valid @RequestBody PlanTaskCreateRequest request) {
        return planTaskService.create(actor, planId, request);
    }

    @PutMapping("/{taskId}")
    @PreAuthorize("hasAuthority('plan:update')")
    public PlanTaskResponse update(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID planId,
            @PathVariable UUID taskId,
            @Valid @RequestBody PlanTaskUpdateRequest request) {
        return planTaskService.update(actor, planId, taskId, request);
    }

    @DeleteMapping("/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('plan:update')")
    public void delete(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID planId,
            @PathVariable UUID taskId) {
        planTaskService.delete(actor, planId, taskId);
    }

    @PutMapping("/{taskId}/move")
    @PreAuthorize("hasAuthority('plan:update')")
    public PlanTaskResponse move(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID planId,
            @PathVariable UUID taskId,
            @Valid @RequestBody PlanTaskMoveRequest request) {
        return planTaskService.move(actor, planId, taskId, request);
    }

    @PostMapping("/{taskId}/dependencies")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('plan:update')")
    public DependencyResponse createDependency(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID planId,
            @PathVariable UUID taskId,
            @Valid @RequestBody DependencyCreateRequest request) {
        return planTaskDependencyService.create(actor, planId, taskId, request);
    }

    @GetMapping("/dependencies")
    @PreAuthorize("hasAuthority('plan:view')")
    public List<DependencyResponse> listDependencies(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID planId) {
        return planTaskDependencyService.list(planId, actor);
    }

    @DeleteMapping("/{taskId}/dependencies/{dependencyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('plan:update')")
    public void deleteDependency(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID planId,
            @PathVariable UUID taskId,
            @PathVariable UUID dependencyId) {
        planTaskDependencyService.delete(actor, planId, taskId, dependencyId);
    }
}
