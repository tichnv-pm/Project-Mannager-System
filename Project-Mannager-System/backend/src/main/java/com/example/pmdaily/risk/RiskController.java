package com.example.pmdaily.risk;

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
import com.example.pmdaily.issue.dto.IssueResponse;
import com.example.pmdaily.risk.dto.RiskCreateRequest;
import com.example.pmdaily.risk.dto.RiskConvertToIssueRequest;
import com.example.pmdaily.risk.dto.RiskResponse;
import com.example.pmdaily.risk.dto.RiskUpdateRequest;
import com.example.pmdaily.security.UserPrincipal;

import jakarta.validation.Valid;

/**
 * REST API Risk (docs/api/08-risk-api.md) — 6 endpoints.
 */
@RestController
@RequestMapping("/api/v1/risks")
public class RiskController {

    private final RiskService riskService;

    public RiskController(RiskService riskService) {
        this.riskService = riskService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('risk:view')")
    public PageResponse<RiskResponse> list(
            @AuthenticationPrincipal UserPrincipal actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) RiskStatus status,
            @RequestParam(required = false) RiskLevel level,
            @RequestParam(required = false) UUID ownerId) {
        return riskService.search(actor, keyword, projectId, status, level, ownerId, page, size, sort);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('risk:manage')")
    public RiskResponse create(
            @AuthenticationPrincipal UserPrincipal actor,
            @Valid @RequestBody RiskCreateRequest request) {
        return riskService.create(actor, request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('risk:view')")
    public RiskResponse get(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id) {
        return riskService.get(id, actor);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('risk:view')")
    public RiskResponse update(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id,
            @Valid @RequestBody RiskUpdateRequest request) {
        return riskService.update(actor, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('risk:manage')")
    public void delete(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id) {
        riskService.delete(actor, id);
    }

    @PostMapping("/{id}/convert-to-issue")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('risk:manage')")
    public IssueResponse convertToIssue(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id,
            @RequestBody(required = false) RiskConvertToIssueRequest request) {
        return riskService.convertToIssue(actor, id, request);
    }
}
