package com.example.pmdaily.report;

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

import jakarta.validation.Valid;

import com.example.pmdaily.report.dto.ReportShareLinkCreateRequest;
import com.example.pmdaily.report.dto.ReportShareLinkResponse;
import com.example.pmdaily.security.UserPrincipal;

/**
 * Quản lý share-link báo cáo tiến độ cho stakeholder ngoài tổ chức (GĐ4 cải tiến,
 * docs/api/12-report-audit-api.md). Endpoint công khai xem báo cáo ở {@link PublicReportController}.
 */
@RestController
@RequestMapping("/api/v1")
public class ReportShareLinkController {

    private final ReportShareLinkService shareLinkService;

    public ReportShareLinkController(ReportShareLinkService shareLinkService) {
        this.shareLinkService = shareLinkService;
    }

    @PostMapping("/projects/{projectId}/report-share-links")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('report:share')")
    public ReportShareLinkResponse create(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID projectId,
            @Valid @RequestBody ReportShareLinkCreateRequest request) {
        return shareLinkService.create(actor, projectId, request);
    }

    @GetMapping("/projects/{projectId}/report-share-links")
    @PreAuthorize("hasAuthority('report:share')")
    public List<ReportShareLinkResponse> list(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID projectId) {
        return shareLinkService.list(actor, projectId);
    }

    @DeleteMapping("/report-share-links/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('report:share')")
    public void revoke(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id) {
        shareLinkService.revoke(actor, id);
    }
}
