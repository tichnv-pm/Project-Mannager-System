package com.example.pmdaily.milestone;

import java.time.Instant;
import java.time.LocalDate;
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
import com.example.pmdaily.milestone.dto.MilestoneCreateRequest;
import com.example.pmdaily.milestone.dto.MilestoneResponse;
import com.example.pmdaily.milestone.dto.MilestoneUpdateRequest;
import com.example.pmdaily.milestone.mapper.MilestoneMapper;
import com.example.pmdaily.project.Project;
import com.example.pmdaily.project.ProjectMemberRepository;
import com.example.pmdaily.project.ProjectRepository;
import com.example.pmdaily.security.UserPrincipal;

@Service
@Transactional(readOnly = true)
public class MilestoneService {

    private static final Logger log = LoggerFactory.getLogger(MilestoneService.class);

    private final MilestoneRepository milestoneRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final MilestoneMapper milestoneMapper;
    private final AuditService auditService;

    public MilestoneService(
            MilestoneRepository milestoneRepository,
            ProjectRepository projectRepository,
            ProjectMemberRepository memberRepository,
            MilestoneMapper milestoneMapper,
            AuditService auditService) {
        this.milestoneRepository = milestoneRepository;
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.milestoneMapper = milestoneMapper;
        this.auditService = auditService;
    }

    public PageResponse<MilestoneResponse> search(
            UserPrincipal actor,
            String keyword,
            UUID projectId,
            MilestoneStatus status,
            int page,
            int size,
            String sortStr) {
        Pageable pageable = createPageable(page, size, sortStr);
        Specification<Milestone> spec = Specification.where(MilestoneSpecification.notDeleted())
                .and(MilestoneSpecification.keyword(keyword))
                .and(MilestoneSpecification.projectId(projectId))
                .and(MilestoneSpecification.status(status));

        if (!actor.getRoles().contains("ADMIN")) {
            spec = spec.and(MilestoneSpecification.memberOf(actor.getId()));
        }

        var milestonePage = milestoneRepository.findAll(spec, pageable);
        return PageResponse.of(milestonePage, milestoneMapper::toResponse);
    }

    public MilestoneResponse get(UUID id, UserPrincipal actor) {
        Milestone milestone = findActive(id);
        checkProjectAccess(actor, milestone.getProject().getId());
        return milestoneMapper.toResponse(milestone);
    }

    @Transactional
    public MilestoneResponse create(UserPrincipal actor, MilestoneCreateRequest request) {
        Project project = projectRepository.findById(request.projectId())
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Dự án", request.projectId()));

        checkProjectAccess(actor, project.getId());

        Milestone milestone = new Milestone();
        milestone.setProject(project);
        milestone.setName(request.name().trim());
        milestone.setDescription(request.description());
        milestone.setPlannedDate(request.plannedDate());
        milestone.setNote(request.note());
        milestone.setStatus(MilestoneStatus.NOT_STARTED);
        milestone.setProgress(0);

        Milestone saved = milestoneRepository.save(milestone);

        auditService.record("MILESTONE_CREATED", "MILESTONE", saved.getId(),
                Map.of("name", saved.getName(), "projectId", project.getId()));

        log.info("milestone.create success id={} name={} actor={}", saved.getId(), saved.getName(), actor.getUsername());
        return milestoneMapper.toResponse(saved);
    }

    @Transactional
    public MilestoneResponse update(UserPrincipal actor, UUID id, MilestoneUpdateRequest request) {
        Milestone milestone = findActive(id);
        checkProjectAccess(actor, milestone.getProject().getId());

        if (!Objects.equals(milestone.getVersion(), request.version())) {
            throw new ConflictException("Record modified by another transaction");
        }

        if (request.status() == MilestoneStatus.COMPLETED && request.progress() < 100) {
            throw new BusinessException(ErrorCode.PROGRESS_REQUIRED_FOR_DONE, "COMPLETED status requires progress = 100");
        }

        milestone.setName(request.name().trim());
        milestone.setDescription(request.description());
        milestone.setPlannedDate(request.plannedDate());
        milestone.setNote(request.note());

        MilestoneStatus oldStatus = milestone.getStatus();
        milestone.setStatus(request.status());
        milestone.setProgress(request.progress());

        if (request.status() == MilestoneStatus.COMPLETED) {
            milestone.setActualDate(request.actualDate() != null ? request.actualDate() : LocalDate.now());
        } else {
            milestone.setActualDate(request.actualDate());
        }

        Milestone updated = milestoneRepository.save(milestone);

        auditService.record("MILESTONE_UPDATED", "MILESTONE", updated.getId(),
                Map.of("name", updated.getName(), "oldStatus", oldStatus, "newStatus", updated.getStatus()));

        log.info("milestone.update success id={} name={} actor={}", updated.getId(), updated.getName(), actor.getUsername());
        return milestoneMapper.toResponse(updated);
    }

    @Transactional
    public void delete(UserPrincipal actor, UUID id) {
        Milestone milestone = findActive(id);
        checkProjectAccess(actor, milestone.getProject().getId());

        milestone.setDeletedAt(Instant.now());
        milestone.setDeletedBy(actor.getId());
        milestoneRepository.save(milestone);

        auditService.record("MILESTONE_DELETED", "MILESTONE", milestone.getId(),
                Map.of("name", milestone.getName()));

        log.info("milestone.delete success id={} name={} actor={}", milestone.getId(), milestone.getName(), actor.getUsername());
    }

    private Milestone findActive(UUID id) {
        return milestoneRepository.findById(id)
                .filter(m -> m.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", id));
    }

    private void checkProjectAccess(UserPrincipal actor, UUID projectId) {
        if (actor.getRoles().contains("ADMIN")) {
            return;
        }
        if (!memberRepository.existsByProjectIdAndUser_Id(projectId, actor.getId())) {
            throw new AccessDeniedException("Access denied to project");
        }
    }

    private Pageable createPageable(int page, int size, String sortStr) {
        if (sortStr == null || sortStr.isBlank()) {
            return PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "plannedDate"));
        }
        String[] parts = sortStr.split(",");
        String property = parts[0].trim();
        Sort.Direction direction = (parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim()))
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return PageRequest.of(page, size, Sort.by(direction, property));
    }
}
