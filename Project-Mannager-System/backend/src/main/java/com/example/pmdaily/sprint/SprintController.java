package com.example.pmdaily.sprint;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.example.pmdaily.security.UserPrincipal;
import com.example.pmdaily.sprint.dto.BurndownPointResponse;
import com.example.pmdaily.sprint.dto.SprintCreateRequest;
import com.example.pmdaily.sprint.dto.SprintResponse;
import com.example.pmdaily.sprint.dto.SprintUpdateRequest;
import com.example.pmdaily.sprint.dto.SprintVelocityResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class SprintController {

    private final SprintService sprintService;

    public SprintController(SprintService sprintService) {
        this.sprintService = sprintService;
    }

    @GetMapping("/projects/{projectId}/sprints")
    @PreAuthorize("hasAuthority('project:view')")
    public List<SprintResponse> list(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID projectId) {
        return sprintService.getSprints(projectId, actor);
    }

    @PostMapping("/projects/{projectId}/sprints")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('project:update')")
    public SprintResponse create(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID projectId,
            @Valid @RequestBody SprintCreateRequest request) {
        return sprintService.createSprint(projectId, request, actor);
    }

    @PutMapping("/sprints/{id}")
    @PreAuthorize("hasAuthority('project:update')")
    public SprintResponse update(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id,
            @Valid @RequestBody SprintUpdateRequest request) {
        return sprintService.updateSprint(id, request, actor);
    }

    @DeleteMapping("/sprints/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('project:update')")
    public void delete(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id) {
        sprintService.deleteSprint(id, actor);
    }

    @GetMapping("/sprints/{id}/velocity")
    @PreAuthorize("hasAuthority('project:view')")
    public SprintVelocityResponse getVelocity(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id) {
        return sprintService.getVelocity(id, actor);
    }

    @GetMapping("/sprints/{id}/burndown")
    @PreAuthorize("hasAuthority('project:view')")
    public List<BurndownPointResponse> getBurndown(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id) {
        return sprintService.getBurndown(id, actor);
    }

    @GetMapping("/projects/{projectId}/sprints/velocity-trend")
    @PreAuthorize("hasAuthority('project:view')")
    public List<SprintVelocityResponse> getVelocityTrend(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID projectId) {
        return sprintService.getVelocityTrend(projectId, actor);
    }
}
