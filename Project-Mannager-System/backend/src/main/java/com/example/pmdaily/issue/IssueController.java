package com.example.pmdaily.issue;

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
import com.example.pmdaily.issue.dto.IssueCreateRequest;
import com.example.pmdaily.issue.dto.IssueResponse;
import com.example.pmdaily.issue.dto.IssueUpdateRequest;
import com.example.pmdaily.security.UserPrincipal;

import jakarta.validation.Valid;

/**
 * REST API Issue (docs/api/09-issue-api.md) — 5 endpoints.
 */
@RestController
@RequestMapping("/api/v1/issues")
public class IssueController {

    private final IssueService issueService;

    public IssueController(IssueService issueService) {
        this.issueService = issueService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('issue:view')")
    public PageResponse<IssueResponse> list(
            @AuthenticationPrincipal UserPrincipal actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) IssueStatus status,
            @RequestParam(required = false) IssueSeverity severity,
            @RequestParam(required = false) UUID ownerId) {
        return issueService.search(actor, keyword, projectId, status, severity, ownerId, page, size, sort);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('issue:manage')")
    public IssueResponse create(
            @AuthenticationPrincipal UserPrincipal actor,
            @Valid @RequestBody IssueCreateRequest request) {
        return issueService.create(actor, request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('issue:view')")
    public IssueResponse get(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id) {
        return issueService.get(id, actor);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('issue:view')")
    public IssueResponse update(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id,
            @Valid @RequestBody IssueUpdateRequest request) {
        return issueService.update(actor, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('issue:manage')")
    public void delete(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id) {
        issueService.delete(actor, id);
    }
}
