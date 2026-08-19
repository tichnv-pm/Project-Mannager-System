package com.example.pmdaily.project;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.pmdaily.audit.AuditService;
import com.example.pmdaily.exception.ResourceNotFoundException;
import com.example.pmdaily.project.dto.EvmSnapshotResponse;
import com.example.pmdaily.project.dto.ProjectMemberFinanceResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class ProjectFinanceService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final ProjectFinancialSnapshotRepository snapshotRepository;
    private final EvmScheduler evmScheduler;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<EvmSnapshotResponse> getEvmSnapshots(UUID projectId) {
        return snapshotRepository.findByProjectIdOrderBySnapshotDateAsc(projectId).stream()
                .map(s -> new EvmSnapshotResponse(
                        s.getId(),
                        s.getSnapshotDate(),
                        s.getPlannedValue(),
                        s.getEarnedValue(),
                        s.getActualCost(),
                        s.getCostVariance(),
                        s.getScheduleVariance(),
                        s.getCpi(),
                        s.getSpi()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProjectMemberFinanceResponse> getProjectMembersFinance(UUID projectId) {
        return memberRepository.findByProjectIdOrderByCreatedAtAsc(projectId).stream()
                .map(m -> new ProjectMemberFinanceResponse(
                        m.getId(),
                        m.getUser().getId(),
                        m.getUser().getUsername(),
                        m.getUser().getFullName(),
                        m.getRole().name(),
                        m.getHourlyRate()
                ))
                .toList();
    }

    public void updateMemberRate(UUID projectId, UUID memberId, Double newRate) {
        ProjectMember member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Thành viên dự án", memberId));

        if (!member.getProject().getId().equals(projectId)) {
            throw new IllegalArgumentException("Thành viên không thuộc dự án này");
        }

        Double oldRate = member.getHourlyRate();
        member.setHourlyRate(newRate);
        memberRepository.save(member);

        auditService.record("MEMBER_RATE_UPDATED", "PROJECT_MEMBER", memberId,
                Map.of("username", member.getUser().getUsername(),
                        "oldRate", String.valueOf(oldRate),
                        "newRate", String.valueOf(newRate)));
    }

    public void recalculateEvm(UUID projectId, LocalDate date, String actorUsername) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Dự án", projectId));

        evmScheduler.calculateAndSaveSnapshot(project, date);

        auditService.record("EVM_RECALCULATED", "PROJECT", projectId,
                Map.of("targetDate", date.toString(), "actor", actorUsername));
    }
}
