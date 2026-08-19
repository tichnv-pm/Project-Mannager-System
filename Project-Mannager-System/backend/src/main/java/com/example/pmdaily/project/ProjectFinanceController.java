package com.example.pmdaily.project;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.example.pmdaily.exception.ResourceNotFoundException;
import com.example.pmdaily.project.dto.EvmSnapshotResponse;
import com.example.pmdaily.project.dto.ProjectMemberFinanceResponse;
import com.example.pmdaily.security.UserPrincipal;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/finance")
@RequiredArgsConstructor
public class ProjectFinanceController {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final ProjectFinanceService financeService;

    @GetMapping("/evm")
    @PreAuthorize("hasAuthority('financial:view')")
    public ResponseEntity<List<EvmSnapshotResponse>> getEvmSnapshots(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID projectId) {
        
        verifyProjectAndAccess(projectId, actor);
        return ResponseEntity.ok(financeService.getEvmSnapshots(projectId));
    }

    @GetMapping("/members")
    @PreAuthorize("hasAuthority('financial:view')")
    public ResponseEntity<List<ProjectMemberFinanceResponse>> getProjectMembersFinance(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID projectId) {

        verifyProjectAndAccess(projectId, actor);
        return ResponseEntity.ok(financeService.getProjectMembersFinance(projectId));
    }

    @PutMapping("/members/{memberId}/rate")
    @PreAuthorize("hasAuthority('financial:update')")
    public ResponseEntity<Void> updateMemberRate(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID projectId,
            @PathVariable UUID memberId,
            @RequestBody Map<String, Double> body) {

        verifyProjectAndAccess(projectId, actor);
        financeService.updateMemberRate(projectId, memberId, body.get("hourlyRate"));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/recalculate")
    @PreAuthorize("hasAuthority('financial:update')")
    public ResponseEntity<Void> recalculateEvm(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID projectId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        verifyProjectAndAccess(projectId, actor);
        LocalDate targetDate = (date != null) ? date : LocalDate.now();
        financeService.recalculateEvm(projectId, targetDate, actor.getUsername());
        return ResponseEntity.ok().build();
    }

    private void verifyProjectAndAccess(UUID projectId, UserPrincipal actor) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Dự án", projectId);
        }
        if (!actor.getRoles().contains("ADMIN") && 
            !memberRepository.existsByProjectIdAndUser_Id(projectId, actor.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Không có quyền truy cập dự án này");
        }
    }
}
