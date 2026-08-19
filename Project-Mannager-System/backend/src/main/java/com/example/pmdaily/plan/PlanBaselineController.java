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

import com.example.pmdaily.plan.dto.BaselineCreateRequest;
import com.example.pmdaily.plan.dto.BaselineResponse;
import com.example.pmdaily.plan.dto.BaselineVarianceResponse;
import com.example.pmdaily.plan.dto.VersionCreateRequest;
import com.example.pmdaily.plan.dto.VersionDiffResponse;
import com.example.pmdaily.plan.dto.VersionResponse;
import com.example.pmdaily.security.UserPrincipal;

import jakarta.validation.Valid;

/**
 * REST API Version & Baseline (docs/api/13-planning-api.md muc 2.5) — PLN-FR-VERSION-*, PLN-FR-BASE-*.
 */
@RestController
@RequestMapping("/api/v1/plans/{planId}")
public class PlanBaselineController {

    private final PlanVersionService versionService;
    private final PlanBaselineService baselineService;

    public PlanBaselineController(PlanVersionService versionService, PlanBaselineService baselineService) {
        this.versionService = versionService;
        this.baselineService = baselineService;
    }

    @PostMapping("/versions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('plan:version')")
    public VersionResponse createVersion(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID planId,
            @Valid @RequestBody VersionCreateRequest request) {
        return versionService.create(actor, planId, request.note());
    }

    @GetMapping("/versions")
    @PreAuthorize("hasAuthority('plan:view')")
    public List<VersionResponse> versions(@PathVariable UUID planId) {
        return versionService.list(planId);
    }

    @GetMapping("/versions/{versionNo}/diff")
    @PreAuthorize("hasAuthority('plan:view')")
    public VersionDiffResponse diff(
            @PathVariable UUID planId,
            @PathVariable int versionNo) {
        return versionService.diff(planId, versionNo);
    }

    @PostMapping("/baselines")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('plan:baseline')")
    public BaselineResponse createBaseline(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID planId,
            @Valid @RequestBody BaselineCreateRequest request) {
        return baselineService.create(actor, planId, request.description());
    }

    @GetMapping("/baselines")
    @PreAuthorize("hasAuthority('plan:view')")
    public List<BaselineResponse> baselines(@PathVariable UUID planId) {
        return baselineService.list(planId);
    }

    @GetMapping("/baselines/{baselineNum}/variance")
    @PreAuthorize("hasAuthority('plan:view')")
    public BaselineVarianceResponse variance(
            @PathVariable UUID planId,
            @PathVariable int baselineNum) {
        return baselineService.variance(planId, baselineNum);
    }

    @DeleteMapping("/baselines/{baselineNum}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('plan:baseline')")
    public void deleteBaseline(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID planId,
            @PathVariable int baselineNum) {
        baselineService.delete(actor, planId, baselineNum);
    }
}