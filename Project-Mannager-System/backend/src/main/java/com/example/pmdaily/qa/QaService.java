package com.example.pmdaily.qa;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.pmdaily.common.ErrorCode;
import com.example.pmdaily.exception.BusinessException;
import com.example.pmdaily.exception.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import com.example.pmdaily.issue.Issue;
import com.example.pmdaily.issue.IssueRepository;
import com.example.pmdaily.issue.IssueSeverity;
import com.example.pmdaily.issue.IssueStatus;
import com.example.pmdaily.project.Project;
import com.example.pmdaily.project.ProjectMember;
import com.example.pmdaily.project.ProjectMemberRepository;
import com.example.pmdaily.project.ProjectMemberRole;
import com.example.pmdaily.project.ProjectRepository;
import com.example.pmdaily.qa.dto.*;
import com.example.pmdaily.security.UserPrincipal;
import com.example.pmdaily.user.User;
import com.example.pmdaily.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QaService {

    private final TestCaseRepository testCaseRepository;
    private final TestRunRepository testRunRepository;
    private final TestResultRepository testResultRepository;
    private final ProjectMemberRepository memberRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final IssueRepository issueRepository;

    private void checkProjectViewAccess(UserPrincipal actor, UUID projectId) {
        if (actor.getRoles().contains("ADMIN")) {
            return;
        }
        if (!memberRepository.existsByProjectIdAndUser_Id(projectId, actor.getId())) {
            throw new AccessDeniedException("Không có quyền truy cập dự án");
        }
    }

    private void checkProjectManageAccess(UserPrincipal actor, UUID projectId) {
        if (actor.getRoles().contains("ADMIN")) {
            return;
        }
        ProjectMember member = memberRepository.findByProjectIdAndUser_Id(projectId, actor.getId())
                .orElseThrow(() -> new AccessDeniedException("Không có quyền truy cập dự án"));
        // Dev/Tester/PM are all allowed to edit QA elements
        if (member.getRole() != ProjectMemberRole.PROJECT_MANAGER && 
            !"TESTER".equals(member.getRole().name()) && 
            !"DEVELOPER".equals(member.getRole().name()) &&
            !"DEV".equals(member.getRole().name())) {
            throw new AccessDeniedException("Cần quyền PM, DEV hoặc TESTER để thực hiện thao tác này");
        }
    }

    // ─── TEST CASES ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TestCaseResponse> getTestCases(UUID projectId, UserPrincipal actor) {
        checkProjectViewAccess(actor, projectId);
        return testCaseRepository.findByProjectIdAndDeletedAtIsNull(projectId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public TestCaseResponse createTestCase(UUID projectId, TestCaseCreateRequest request, UserPrincipal actor) {
        checkProjectManageAccess(actor, projectId);

        TestCase tc = new TestCase();
        tc.setProjectId(projectId);
        tc.setTitle(request.title().trim());
        tc.setDescription(request.description());
        tc.setPreconditions(request.preconditions());
        tc.setPriority(request.priority() != null ? request.priority() : "MEDIUM");
        tc.setStatus("DRAFT");
        tc.setCreatedBy(actor.getId());
        tc.setUpdatedBy(actor.getId());

        if (request.steps() != null) {
            for (TestStepDto s : request.steps()) {
                TestStep step = new TestStep();
                step.setTestCase(tc);
                step.setStepNumber(s.stepNumber());
                step.setAction(s.action().trim());
                step.setExpectedResult(s.expectedResult().trim());
                tc.getSteps().add(step);
            }
        }

        TestCase saved = testCaseRepository.save(tc);
        return toResponse(saved);
    }

    @Transactional
    public TestCaseResponse updateTestCase(UUID testCaseId, TestCaseUpdateRequest request, UserPrincipal actor) {
        TestCase tc = testCaseRepository.findByIdAndDeletedAtIsNull(testCaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Test case", testCaseId));
        checkProjectManageAccess(actor, tc.getProjectId());

        tc.setTitle(request.title().trim());
        tc.setDescription(request.description());
        tc.setPreconditions(request.preconditions());
        tc.setPriority(request.priority() != null ? request.priority() : "MEDIUM");
        tc.setStatus(request.status() != null ? request.status() : "DRAFT");
        tc.setUpdatedBy(actor.getId());

        // Replace steps
        tc.getSteps().clear();
        if (request.steps() != null) {
            for (TestStepDto s : request.steps()) {
                TestStep step = new TestStep();
                step.setTestCase(tc);
                step.setStepNumber(s.stepNumber());
                step.setAction(s.action().trim());
                step.setExpectedResult(s.expectedResult().trim());
                tc.getSteps().add(step);
            }
        }

        TestCase saved = testCaseRepository.save(tc);
        return toResponse(saved);
    }

    @Transactional
    public void deleteTestCase(UUID testCaseId, UserPrincipal actor) {
        TestCase tc = testCaseRepository.findByIdAndDeletedAtIsNull(testCaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Test case", testCaseId));
        checkProjectManageAccess(actor, tc.getProjectId());

        tc.setDeletedAt(Instant.now());
        tc.setDeletedBy(actor.getId());
        testCaseRepository.save(tc);
    }

    // ─── TEST RUNS ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TestRunResponse> getTestRuns(UUID projectId, UserPrincipal actor) {
        checkProjectViewAccess(actor, projectId);
        return testRunRepository.findByProjectIdAndDeletedAtIsNull(projectId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public TestRunResponse createTestRun(UUID projectId, TestRunCreateRequest request, UserPrincipal actor) {
        checkProjectManageAccess(actor, projectId);

        TestRun tr = new TestRun();
        tr.setProjectId(projectId);
        tr.setName(request.name().trim());
        tr.setDescription(request.description());
        tr.setStatus("PENDING");
        tr.setCreatedBy(actor.getId());
        tr.setUpdatedBy(actor.getId());

        TestRun savedRun = testRunRepository.save(tr);

        // Add default UNTESTED result mapping for each test case
        for (UUID tcId : request.testCaseIds()) {
            TestCase tc = testCaseRepository.findByIdAndDeletedAtIsNull(tcId)
                    .orElseThrow(() -> new ResourceNotFoundException("Test case", tcId));
            if (!tc.getProjectId().equals(projectId)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Kịch bản test " + tcId + " không thuộc dự án này");
            }

            TestResult res = new TestResult();
            res.setTestRunId(savedRun.getId());
            res.setTestCaseId(tcId);
            res.setStatus("UNTESTED");
            testResultRepository.save(res);
        }

        return toResponse(savedRun);
    }

    @Transactional(readOnly = true)
    public List<TestResultResponse> getTestResults(UUID testRunId, UserPrincipal actor) {
        TestRun tr = testRunRepository.findByIdAndDeletedAtIsNull(testRunId)
                .orElseThrow(() -> new ResourceNotFoundException("Test run", testRunId));
        checkProjectViewAccess(actor, tr.getProjectId());

        List<TestResult> results = testResultRepository.findByTestRunId(testRunId);
        List<TestResultResponse> response = new ArrayList<>();

        for (TestResult r : results) {
            TestCase tc = testCaseRepository.findById(r.getTestCaseId()).orElse(null);
            String executorName = null;
            if (r.getExecutedBy() != null) {
                executorName = userRepository.findById(r.getExecutedBy())
                        .map(u -> u.getFullName() != null ? u.getFullName() : u.getUsername())
                        .orElse(null);
            }
            response.add(toResultResponse(r, tc, executorName));
        }

        return response;
    }

    @Transactional
    public TestResultResponse updateTestResult(UUID testRunId, UUID testCaseId, TestResultUpdateRequest request, UserPrincipal actor) {
        TestRun tr = testRunRepository.findByIdAndDeletedAtIsNull(testRunId)
                .orElseThrow(() -> new ResourceNotFoundException("Test run", testRunId));
        checkProjectManageAccess(actor, tr.getProjectId());

        TestResult result = testResultRepository.findByTestRunIdAndTestCaseId(testRunId, testCaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Test result", testCaseId));

        TestCase tc = testCaseRepository.findByIdAndDeletedAtIsNull(testCaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Test case", testCaseId));

        String oldStatus = result.getStatus();
        result.setStatus(request.status());
        result.setActualResult(request.actualResult());
        result.setExecutedBy(actor.getId());
        result.setExecutedAt(Instant.now());

        TestResult savedResult = testResultRepository.save(result);

        // Update TestRun status to IN_PROGRESS if first test started
        if ("PENDING".equals(tr.getStatus())) {
            tr.setStatus("IN_PROGRESS");
            testRunRepository.save(tr);
        }

        // Check if all test cases in this run are completed, then set status to COMPLETED
        List<TestResult> allResults = testResultRepository.findByTestRunId(testRunId);
        boolean allFinished = allResults.stream().noneMatch(r -> "UNTESTED".equals(r.getStatus()));
        if (allFinished) {
            tr.setStatus("COMPLETED");
            testRunRepository.save(tr);
        }

        // Automatic bug creation workflow
        if ("FAILED".equals(request.status()) && !"FAILED".equals(oldStatus)) {
            createAutoBug(tr.getProjectId(), tc, request.actualResult(), actor);
        }

        String executorName = userRepository.findById(actor.getId())
                .map(u -> u.getFullName() != null ? u.getFullName() : u.getUsername())
                .orElse(null);

        return toResultResponse(savedResult, tc, executorName);
    }

    private void createAutoBug(UUID projectId, TestCase tc, String actualResult, UserPrincipal actor) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Dự án", projectId));

        User executor = userRepository.findById(actor.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng", actor.getId()));

        Issue bug = new Issue();
        bug.setProject(project);
        bug.setCode(generateIssueCode());
        bug.setTitle("[BUG] Thất bại tại kiểm thử: " + tc.getTitle());

        StringBuilder desc = new StringBuilder();
        desc.append("Kịch bản kiểm thử bị thất bại.\n\n");
        desc.append("Tiền đề: ").append(tc.getPreconditions() != null ? tc.getPreconditions() : "Không có").append("\n\n");
        desc.append("Các bước thực hiện:\n");
        if (tc.getSteps() != null) {
            for (TestStep step : tc.getSteps()) {
                desc.append(step.getStepNumber()).append(". Action: ").append(step.getAction())
                        .append(" | Expected: ").append(step.getExpectedResult()).append("\n");
            }
        }
        desc.append("\nKết quả thực tế bị lỗi:\n")
                .append(actualResult != null && !actualResult.isBlank() ? actualResult : "Không ghi nhận chi tiết lỗi");

        bug.setDescription(desc.toString());
        bug.setSeverity(IssueSeverity.HIGH);
        bug.setOwner(executor);
        bug.setStatus(IssueStatus.OPEN);
        bug.setTestCaseId(tc.getId());

        issueRepository.save(bug);
    }

    private String generateIssueCode() {
        long count = issueRepository.count() + 1;
        String code = String.format("ISS%06d", count);
        while (issueRepository.existsByCode(code)) {
            count++;
            code = String.format("ISS%06d", count);
        }
        return code;
    }

    private TestCaseResponse toResponse(TestCase tc) {
        return new TestCaseResponse(
                tc.getId(),
                tc.getProjectId(),
                tc.getTitle(),
                tc.getDescription(),
                tc.getPreconditions(),
                tc.getPriority(),
                tc.getStatus(),
                tc.getSteps().stream()
                        .map(s -> new TestStepDto(s.getStepNumber(), s.getAction(), s.getExpectedResult()))
                        .toList()
        );
    }

    private TestRunResponse toResponse(TestRun tr) {
        return new TestRunResponse(
                tr.getId(),
                tr.getProjectId(),
                tr.getName(),
                tr.getDescription(),
                tr.getStatus(),
                tr.getCreatedAt(),
                tr.getCreatedBy()
        );
    }

    private TestResultResponse toResultResponse(TestResult r, TestCase tc, String executorName) {
        UUID bugIssueId = null;
        String bugIssueCode = null;
        if ("FAILED".equals(r.getStatus()) && tc != null) {
            List<Issue> bugs = issueRepository.findByTestCaseIdAndDeletedAtIsNull(tc.getId());
            if (!bugs.isEmpty()) {
                Issue latestBug = bugs.get(bugs.size() - 1);
                bugIssueId = latestBug.getId();
                bugIssueCode = latestBug.getCode();
            }
        }

        return new TestResultResponse(
                r.getId(),
                r.getTestRunId(),
                r.getTestCaseId(),
                tc != null ? tc.getTitle() : "Kịch bản đã xóa",
                r.getStatus(),
                r.getActualResult(),
                r.getExecutedBy(),
                executorName,
                r.getExecutedAt(),
                bugIssueId,
                bugIssueCode
        );
    }
}
