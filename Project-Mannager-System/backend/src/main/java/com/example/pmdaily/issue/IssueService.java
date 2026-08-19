package com.example.pmdaily.issue;

import java.time.Instant;
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
import com.example.pmdaily.issue.dto.IssueCreateRequest;
import com.example.pmdaily.issue.dto.IssueResponse;
import com.example.pmdaily.issue.dto.IssueUpdateRequest;
import com.example.pmdaily.issue.mapper.IssueMapper;
import com.example.pmdaily.project.Project;
import com.example.pmdaily.project.ProjectMemberRepository;
import com.example.pmdaily.project.ProjectRepository;
import com.example.pmdaily.security.UserPrincipal;
import com.example.pmdaily.task.dto.UserBriefResponse;
import com.example.pmdaily.user.User;
import com.example.pmdaily.user.UserRepository;

@Service
@Transactional(readOnly = true)
public class IssueService {

    private static final Logger log = LoggerFactory.getLogger(IssueService.class);

    private final IssueRepository issueRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final IssueMapper issueMapper;
    private final AuditService auditService;

    public IssueService(
            IssueRepository issueRepository,
            ProjectRepository projectRepository,
            ProjectMemberRepository memberRepository,
            UserRepository userRepository,
            IssueMapper issueMapper,
            AuditService auditService) {
        this.issueRepository = issueRepository;
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.issueMapper = issueMapper;
        this.auditService = auditService;
    }

    public PageResponse<IssueResponse> search(
            UserPrincipal actor,
            String keyword,
            UUID projectId,
            IssueStatus status,
            IssueSeverity severity,
            UUID ownerId,
            int page,
            int size,
            String sortStr) {
        Pageable pageable = createPageable(page, size, sortStr);
        Specification<Issue> spec = Specification.where(IssueSpecification.notDeleted())
                .and(IssueSpecification.keyword(keyword))
                .and(IssueSpecification.projectId(projectId))
                .and(IssueSpecification.status(status))
                .and(IssueSpecification.severity(severity))
                .and(IssueSpecification.ownerId(ownerId));

        if (!actor.getRoles().contains("ADMIN")) {
            spec = spec.and(IssueSpecification.memberOf(actor.getId()));
        }

        var issuePage = issueRepository.findAll(spec, pageable);
        return PageResponse.of(issuePage, this::mapToResponse);
    }

    public IssueResponse get(UUID id, UserPrincipal actor) {
        Issue issue = findActive(id);
        checkProjectAccess(actor, issue.getProject().getId());
        return mapToResponse(issue);
    }

    @Transactional
    public IssueResponse create(UserPrincipal actor, IssueCreateRequest request) {
        Project project = projectRepository.findById(request.projectId())
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Dự án", request.projectId()));

        checkProjectAccess(actor, project.getId());

        User owner = userRepository.findById(request.ownerId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.ownerId()));

        if (!memberRepository.existsByProjectIdAndUser_Id(project.getId(), owner.getId())) {
            throw new BusinessException(ErrorCode.NOT_PROJECT_MEMBER, "Owner does not belong to project");
        }

        Issue issue = new Issue();
        issue.setCode(generateIssueCode());
        issue.setProject(project);
        issue.setTitle(request.title().trim());
        issue.setDescription(request.description());
        issue.setSeverity(request.severity());
        issue.setOwner(owner);
        issue.setRootCause(request.rootCause());
        issue.setSolution(request.solution());
        issue.setStatus(request.status() != null ? request.status() : IssueStatus.OPEN);
        issue.setDueDate(request.dueDate());

        if (issue.getStatus() == IssueStatus.RESOLVED) {
            issue.setResolvedAt(Instant.now());
        }

        Issue saved = issueRepository.save(issue);

        auditService.record("ISSUE_CREATED", "ISSUE", saved.getId(),
                Map.of("code", saved.getCode(), "title", saved.getTitle(), "projectId", project.getId()));

        log.info("issue.create success id={} code={} actor={}", saved.getId(), saved.getCode(), actor.getUsername());
        return mapToResponse(saved);
    }

    @Transactional
    public IssueResponse update(UserPrincipal actor, UUID id, IssueUpdateRequest request) {
        Issue issue = findActive(id);
        checkProjectAccess(actor, issue.getProject().getId());

        if (!Objects.equals(issue.getVersion(), request.version())) {
            throw new ConflictException("Record modified by another transaction");
        }

        boolean canManage = actor.getRoles().contains("ADMIN") || actor.getPermissions().contains("issue:manage");
        boolean isOwner = Objects.equals(actor.getId(), issue.getOwner().getId());

        if (!canManage && !isOwner) {
            throw new AccessDeniedException("No permission to update issue");
        }

        if (canManage) {
            issue.setTitle(request.title().trim());
            issue.setDescription(request.description());
            issue.setSeverity(request.severity());

            if (!Objects.equals(issue.getOwner().getId(), request.ownerId())) {
                User newOwner = userRepository.findById(request.ownerId())
                        .orElseThrow(() -> new ResourceNotFoundException("User", request.ownerId()));
                if (!memberRepository.existsByProjectIdAndUser_Id(issue.getProject().getId(), newOwner.getId())) {
                    throw new BusinessException(ErrorCode.NOT_PROJECT_MEMBER, "Owner does not belong to project");
                }
                issue.setOwner(newOwner);
            }

            issue.setRootCause(request.rootCause());
            issue.setSolution(request.solution());
            issue.setDueDate(request.dueDate());
        }

        IssueStatus oldStatus = issue.getStatus();
        IssueStatus newStatus = request.status();
        issue.setStatus(newStatus);

        if (newStatus == IssueStatus.RESOLVED && oldStatus != IssueStatus.RESOLVED && issue.getResolvedAt() == null) {
            issue.setResolvedAt(Instant.now());
        } else if (newStatus != IssueStatus.RESOLVED && oldStatus == IssueStatus.RESOLVED) {
            issue.setResolvedAt(null);
        }

        Issue updated = issueRepository.save(issue);

        auditService.record("ISSUE_UPDATED", "ISSUE", updated.getId(),
                Map.of("code", updated.getCode(), "oldStatus", oldStatus, "newStatus", newStatus));

        log.info("issue.update success id={} code={} actor={}", updated.getId(), updated.getCode(), actor.getUsername());
        return mapToResponse(updated);
    }

    @Transactional
    public void delete(UserPrincipal actor, UUID id) {
        Issue issue = findActive(id);
        checkProjectAccess(actor, issue.getProject().getId());

        issue.setDeletedAt(Instant.now());
        issue.setDeletedBy(actor.getId());
        issueRepository.save(issue);

        auditService.record("ISSUE_DELETED", "ISSUE", issue.getId(),
                Map.of("code", issue.getCode(), "title", issue.getTitle()));

        log.info("issue.delete success id={} code={} actor={}", issue.getId(), issue.getCode(), actor.getUsername());
    }

    private Issue findActive(UUID id) {
        return issueRepository.findById(id)
                .filter(i -> i.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Issue", id));
    }

    private void checkProjectAccess(UserPrincipal actor, UUID projectId) {
        if (actor.getRoles().contains("ADMIN")) {
            return;
        }
        if (!memberRepository.existsByProjectIdAndUser_Id(projectId, actor.getId())) {
            throw new AccessDeniedException("Access denied to project");
        }
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

    private IssueResponse mapToResponse(Issue issue) {
        UserBriefResponse owner = issueMapper.toUserBrief(issue.getOwner());
        return issueMapper.toResponse(issue, owner);
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
