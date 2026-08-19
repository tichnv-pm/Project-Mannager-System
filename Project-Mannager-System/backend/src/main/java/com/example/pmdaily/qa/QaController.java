package com.example.pmdaily.qa;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.example.pmdaily.security.UserPrincipal;
import com.example.pmdaily.qa.dto.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class QaController {

    private final QaService qaService;

    public QaController(QaService qaService) {
        this.qaService = qaService;
    }

    // ─── TEST CASES ──────────────────────────────────────────────────

    @GetMapping("/projects/{projectId}/qa/test-cases")
    @PreAuthorize("hasAuthority('project:view')")
    public List<TestCaseResponse> listTestCases(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID projectId) {
        return qaService.getTestCases(projectId, actor);
    }

    @PostMapping("/projects/{projectId}/qa/test-cases")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('project:update')")
    public TestCaseResponse createTestCase(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID projectId,
            @Valid @RequestBody TestCaseCreateRequest request) {
        return qaService.createTestCase(projectId, request, actor);
    }

    @PutMapping("/qa/test-cases/{id}")
    @PreAuthorize("hasAuthority('project:update')")
    public TestCaseResponse updateTestCase(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id,
            @Valid @RequestBody TestCaseUpdateRequest request) {
        return qaService.updateTestCase(id, request, actor);
    }

    @DeleteMapping("/qa/test-cases/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('project:update')")
    public void deleteTestCase(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id) {
        qaService.deleteTestCase(id, actor);
    }

    // ─── TEST RUNS ───────────────────────────────────────────────────

    @GetMapping("/projects/{projectId}/qa/test-runs")
    @PreAuthorize("hasAuthority('project:view')")
    public List<TestRunResponse> listTestRuns(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID projectId) {
        return qaService.getTestRuns(projectId, actor);
    }

    @PostMapping("/projects/{projectId}/qa/test-runs")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('project:update')")
    public TestRunResponse createTestRun(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID projectId,
            @Valid @RequestBody TestRunCreateRequest request) {
        return qaService.createTestRun(projectId, request, actor);
    }

    @GetMapping("/qa/test-runs/{runId}/results")
    @PreAuthorize("hasAuthority('project:view')")
    public List<TestResultResponse> listTestResults(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID runId) {
        return qaService.getTestResults(runId, actor);
    }

    @PutMapping("/qa/test-runs/{runId}/results/{caseId}")
    @PreAuthorize("hasAuthority('project:update')")
    public TestResultResponse updateTestResult(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID runId,
            @PathVariable UUID caseId,
            @Valid @RequestBody TestResultUpdateRequest request) {
        return qaService.updateTestResult(runId, caseId, request, actor);
    }
}
