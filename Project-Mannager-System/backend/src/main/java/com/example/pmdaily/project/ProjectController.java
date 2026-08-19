package com.example.pmdaily.project;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import com.example.pmdaily.project.dto.ProjectCreateRequest;
import com.example.pmdaily.project.dto.ProjectMemberRequest;
import com.example.pmdaily.project.dto.ProjectMemberResponse;
import com.example.pmdaily.project.dto.ProjectMemberRoleRequest;
import com.example.pmdaily.project.dto.ProjectResponse;
import com.example.pmdaily.project.dto.ProjectUpdateRequest;
import com.example.pmdaily.security.UserPrincipal;

/**
 * API dự án & thành viên (docs/api/04-project-api.md).
 * Quyền toàn cục qua @PreAuthorize; kiểm tra kép membership/PM dự án trong ProjectService.
 */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('project:view')")
    public PageResponse<ProjectResponse> list(
            @AuthenticationPrincipal UserPrincipal actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ProjectStatus status,
            @RequestParam(required = false) UUID projectManagerId,
            @RequestParam(required = false, defaultValue = "false") boolean myOnly) {
        return projectService.search(actor.getId(), actor.getRoles(), keyword, status,
                projectManagerId, myOnly, page, size, sort);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('project:create')")
    public ProjectResponse create(@AuthenticationPrincipal UserPrincipal actor,
            @Valid @RequestBody ProjectCreateRequest request) {
        return projectService.create(actor, request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('project:view')")
    public ProjectResponse get(@AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id) {
        return projectService.get(id, actor);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('project:update')")
    public ProjectResponse update(@AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id,
            @Valid @RequestBody ProjectUpdateRequest request) {
        return projectService.update(actor, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('project:delete')")
    public void delete(@AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "false") boolean confirm) {
        projectService.delete(actor, id, confirm);
    }

    @GetMapping("/{id}/members")
    @PreAuthorize("hasAuthority('project:view')")
    public List<ProjectMemberResponse> members(@AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id) {
        return projectService.listMembers(id, actor);
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('project-member:manage')")
    public ProjectMemberResponse addMember(@AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id,
            @Valid @RequestBody ProjectMemberRequest request) {
        return projectService.addMember(actor, id, request);
    }

    @PutMapping("/{id}/members/{userId}")
    @PreAuthorize("hasAuthority('project-member:manage')")
    public ProjectMemberResponse changeRole(@AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id,
            @PathVariable UUID userId,
            @Valid @RequestBody ProjectMemberRoleRequest request) {
        return projectService.changeRole(actor, id, userId, request);
    }

    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('project-member:manage')")
    public void removeMember(@AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id,
            @PathVariable UUID userId) {
        projectService.removeMember(actor, id, userId);
    }
}
