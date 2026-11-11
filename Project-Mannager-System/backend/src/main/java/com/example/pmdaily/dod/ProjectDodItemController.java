package com.example.pmdaily.dod;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.example.pmdaily.dod.dto.ProjectDodItemCreateRequest;
import com.example.pmdaily.dod.dto.ProjectDodItemResponse;
import com.example.pmdaily.dod.dto.ProjectDodItemUpdateRequest;
import com.example.pmdaily.security.UserPrincipal;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/dod-items")
public class ProjectDodItemController {

    private final ProjectDodItemService service;

    public ProjectDodItemController(ProjectDodItemService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('project:view')")
    public List<ProjectDodItemResponse> list(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID projectId) {
        return service.list(projectId, actor);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('project:update')")
    public ProjectDodItemResponse create(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID projectId,
            @Valid @RequestBody ProjectDodItemCreateRequest request) {
        return service.create(projectId, request, actor);
    }

    @PutMapping("/{itemId}")
    @PreAuthorize("hasAuthority('project:update')")
    public ProjectDodItemResponse update(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID projectId,
            @PathVariable UUID itemId,
            @Valid @RequestBody ProjectDodItemUpdateRequest request) {
        return service.update(projectId, itemId, request, actor);
    }

    @DeleteMapping("/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('project:update')")
    public void delete(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID projectId,
            @PathVariable UUID itemId) {
        service.delete(projectId, itemId, actor);
    }
}
