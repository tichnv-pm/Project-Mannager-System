package com.example.pmdaily.risk;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.pmdaily.audit.AuditService;
import com.example.pmdaily.common.ErrorCode;
import com.example.pmdaily.common.PageResponse;
import com.example.pmdaily.exception.BusinessException;
import com.example.pmdaily.exception.ConflictException;
import com.example.pmdaily.exception.ResourceNotFoundException;
import com.example.pmdaily.issue.Issue;
import com.example.pmdaily.issue.IssueSeverity;
import com.example.pmdaily.issue.IssueStatus;
import com.example.pmdaily.issue.dto.IssueCreateRequest;
import com.example.pmdaily.issue.dto.IssueResponse;
import com.example.pmdaily.issue.IssueService;
import com.example.pmdaily.project.Project;
import com.example.pmdaily.project.ProjectMemberRepository;
import com.example.pmdaily.project.ProjectRepository;
import com.example.pmdaily.risk.dto.RiskCreateRequest;
import com.example.pmdaily.risk.dto.RiskConvertToIssueRequest;
import com.example.pmdaily.risk.dto.RiskResponse;
import com.example.pmdaily.risk.dto.RiskUpdateRequest;
import com.example.pmdaily.risk.mapper.RiskMapper;
import com.example.pmdaily.security.UserPrincipal;
import com.example.pmdaily.task.dto.UserBriefResponse;
import com.example.pmdaily.user.User;
import com.example.pmdaily.user.UserRepository;

@Service
@Transactional(readOnly = true)
public class RiskService {

    private static final Logger log = LoggerFactory.getLogger(RiskService.class);

    private final RiskRepository riskRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final IssueService issueService;
    private final RiskMapper riskMapper;
    private final AuditService auditService;

    public RiskService(
            RiskRepository riskRepository,
            ProjectRepository projectRepository,
            ProjectMemberRepository memberRepository,
            UserRepository userRepository,
            IssueService issueService,
            RiskMapper riskMapper,
            AuditService auditService) {
        this.riskRepository = riskRepository;
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.issueService = issueService;
        this.riskMapper = riskMapper;
        this.auditService = auditService;
    }

    public PageResponse<RiskResponse> search(
            UserPrincipal actor,
            String keyword,
            UUID projectId,
            RiskStatus status,
            RiskLevel level,
            UUID ownerId,
            int page,
            int size,
            String sortStr) {
        Pageable pageable = createPageable(page, size, sortStr);
        Specification<Risk> spec = Specification.where(RiskSpecification.notDeleted())
                .and(RiskSpecification.keyword(keyword))
                .and(RiskSpecification.projectId(projectId))
                .and(RiskSpecification.status(status))
                .and(RiskSpecification.level(level))
                .and(RiskSpecification.ownerId(ownerId));

        if (!actor.getRoles().contains("ADMIN")) {
            spec = spec.and(RiskSpecification.memberOf(actor.getId()));
        }

        var riskPage = riskRepository.findAll(spec, pageable);
        return PageResponse.of(riskPage, this::mapToResponse);
    }

    public RiskResponse get(UUID id, UserPrincipal actor) {
        Risk risk = findActive(id);
        checkProjectAccess(actor, risk.getProject().getId());
        return mapToResponse(risk);
    }

    @Transactional
    public RiskResponse create(UserPrincipal actor, RiskCreateRequest request) {
        Project project = projectRepository.findById(request.projectId())
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Dự án", request.projectId()));

        checkProjectAccess(actor, project.getId());

        User owner = userRepository.findById(request.ownerId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.ownerId()));

        if (!memberRepository.existsByProjectIdAndUser_Id(project.getId(), owner.getId())) {
            throw new BusinessException(ErrorCode.NOT_PROJECT_MEMBER, "Owner does not belong to project");
        }

        RiskLevel computedLevel = request.level() != null
                ? request.level()
                : calculateLevel(request.probability(), request.impact());

        Risk risk = new Risk();
        risk.setCode(generateRiskCode());
        risk.setProject(project);
        risk.setTitle(request.title().trim());
        risk.setDescription(request.description());
        risk.setProbability(request.probability());
        risk.setImpact(request.impact());
        risk.setLevel(computedLevel);
        risk.setOwner(owner);
        risk.setMitigationPlan(request.mitigationPlan());
        risk.setContingencyPlan(request.contingencyPlan());
        risk.setStatus(request.status() != null ? request.status() : RiskStatus.OPEN);
        risk.setDueDate(request.dueDate());

        Risk saved = riskRepository.save(risk);

        auditService.record("RISK_CREATED", "RISK", saved.getId(),
                Map.of("code", saved.getCode(), "title", saved.getTitle(), "projectId", project.getId()));

        log.info("risk.create success id={} code={} actor={}", saved.getId(), saved.getCode(), actor.getUsername());
        return mapToResponse(saved);
    }

    @Transactional
    public RiskResponse update(UserPrincipal actor, UUID id, RiskUpdateRequest request) {
        Risk risk = findActive(id);
        checkProjectAccess(actor, risk.getProject().getId());

        if (!Objects.equals(risk.getVersion(), request.version())) {
            throw new ConflictException("Record modified by another transaction");
        }

        boolean canManage = actor.getRoles().contains("ADMIN") || actor.getPermissions().contains("risk:manage");
        boolean isOwner = Objects.equals(actor.getId(), risk.getOwner().getId());

        if (!canManage && !isOwner) {
            throw new AccessDeniedException("No permission to update risk");
        }

        if (canManage) {
            risk.setTitle(request.title().trim());
            risk.setDescription(request.description());
            risk.setProbability(request.probability());
            risk.setImpact(request.impact());

            RiskLevel computedLevel = request.level() != null
                    ? request.level()
                    : calculateLevel(request.probability(), request.impact());
            risk.setLevel(computedLevel);

            if (!Objects.equals(risk.getOwner().getId(), request.ownerId())) {
                User newOwner = userRepository.findById(request.ownerId())
                        .orElseThrow(() -> new ResourceNotFoundException("User", request.ownerId()));
                if (!memberRepository.existsByProjectIdAndUser_Id(risk.getProject().getId(), newOwner.getId())) {
                    throw new BusinessException(ErrorCode.NOT_PROJECT_MEMBER, "Owner does not belong to project");
                }
                risk.setOwner(newOwner);
            }

            risk.setMitigationPlan(request.mitigationPlan());
            risk.setContingencyPlan(request.contingencyPlan());
            risk.setDueDate(request.dueDate());
        }

        RiskStatus oldStatus = risk.getStatus();
        risk.setStatus(request.status());

        Risk updated = riskRepository.save(risk);

        auditService.record("RISK_UPDATED", "RISK", updated.getId(),
                Map.of("code", updated.getCode(), "oldStatus", oldStatus, "newStatus", updated.getStatus()));

        log.info("risk.update success id={} code={} actor={}", updated.getId(), updated.getCode(), actor.getUsername());
        return mapToResponse(updated);
    }

    @Transactional
    public void delete(UserPrincipal actor, UUID id) {
        Risk risk = findActive(id);
        checkProjectAccess(actor, risk.getProject().getId());

        risk.setDeletedAt(java.time.Instant.now());
        risk.setDeletedBy(actor.getId());
        riskRepository.save(risk);

        auditService.record("RISK_DELETED", "RISK", risk.getId(),
                Map.of("code", risk.getCode(), "title", risk.getTitle()));

        log.info("risk.delete success id={} code={} actor={}", risk.getId(), risk.getCode(), actor.getUsername());
    }

    @Transactional
    public IssueResponse convertToIssue(UserPrincipal actor, UUID id, RiskConvertToIssueRequest request) {
        Risk risk = findActive(id);
        checkProjectAccess(actor, risk.getProject().getId());

        if (risk.getStatus() != RiskStatus.OCCURRED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Risk must be in OCCURRED status to convert to issue");
        }

        if (risk.getLinkedIssue() != null) {
            throw new ConflictException("Risk is already converted to an issue");
        }

        IssueSeverity severity = (request != null && request.severity() != null)
                ? request.severity()
                : mapRiskLevelToIssueSeverity(risk.getLevel());

        var issueCreateRequest = new IssueCreateRequest(
                risk.getProject().getId(),
                "Vấn đề phát sinh từ rủi ro " + risk.getCode() + ": " + risk.getTitle(),
                risk.getDescription(),
                severity,
                risk.getOwner().getId(),
                "Rủi ro đã xảy ra (" + risk.getCode() + ")",
                risk.getMitigationPlan(),
                IssueStatus.OPEN,
                (request != null && request.dueDate() != null) ? request.dueDate() : risk.getDueDate()
        );

        IssueResponse createdIssue = issueService.create(actor, issueCreateRequest);

        // Associate created issue entity
        Issue issueEntity = new Issue();
        issueEntity.setId(createdIssue.id());
        risk.setLinkedIssue(issueEntity);
        riskRepository.save(risk);

        auditService.record("RISK_CONVERTED_TO_ISSUE", "RISK", risk.getId(),
                Map.of("riskCode", risk.getCode(), "issueId", createdIssue.id(), "issueCode", createdIssue.code()));

        log.info("risk.convert_to_issue success riskId={} issueId={} actor={}", risk.getId(), createdIssue.id(), actor.getUsername());
        return createdIssue;
    }

    private Risk findActive(UUID id) {
        return riskRepository.findById(id)
                .filter(r -> r.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Risk", id));
    }

    private void checkProjectAccess(UserPrincipal actor, UUID projectId) {
        if (actor.getRoles().contains("ADMIN")) {
            return;
        }
        if (!memberRepository.existsByProjectIdAndUser_Id(projectId, actor.getId())) {
            throw new AccessDeniedException("Access denied to project");
        }
    }

    public static RiskLevel calculateLevel(RiskProbability probability, RiskImpact impact) {
        if (probability == null || impact == null) {
            return RiskLevel.MEDIUM;
        }
        if (probability == RiskProbability.HIGH && impact == RiskImpact.HIGH) {
            return RiskLevel.CRITICAL;
        }
        if ((probability == RiskProbability.HIGH && impact == RiskImpact.MEDIUM)
                || (probability == RiskProbability.MEDIUM && impact == RiskImpact.HIGH)) {
            return RiskLevel.HIGH;
        }
        if ((probability == RiskProbability.LOW && impact == RiskImpact.HIGH)
                || (probability == RiskProbability.MEDIUM && impact == RiskImpact.MEDIUM)
                || (probability == RiskProbability.HIGH && impact == RiskImpact.LOW)) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }

    private IssueSeverity mapRiskLevelToIssueSeverity(RiskLevel level) {
        if (level == null) return IssueSeverity.MEDIUM;
        return switch (level) {
            case CRITICAL -> IssueSeverity.CRITICAL;
            case HIGH -> IssueSeverity.HIGH;
            case MEDIUM -> IssueSeverity.MEDIUM;
            case LOW -> IssueSeverity.LOW;
        };
    }

    private String generateRiskCode() {
        long count = riskRepository.count() + 1;
        String code = String.format("RSK%06d", count);
        while (riskRepository.existsByCode(code)) {
            count++;
            code = String.format("RSK%06d", count);
        }
        return code;
    }

    private RiskResponse mapToResponse(Risk risk) {
        UserBriefResponse owner = riskMapper.toUserBrief(risk.getOwner());
        return riskMapper.toResponse(risk, owner);
    }

    private Pageable createPageable(int page, int size, String sortStr) {
        if (sortStr == null || sortStr.isBlank()) {
            return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        }
        String[] parts = sortStr.split(",");
        String property = parts[0].trim();
        Sort.Direction direction = (parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim()))
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(page, size, Sort.by(direction, property));
    }
}
