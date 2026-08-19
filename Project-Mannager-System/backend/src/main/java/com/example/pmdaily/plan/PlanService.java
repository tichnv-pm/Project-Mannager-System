package com.example.pmdaily.plan;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
import com.example.pmdaily.plan.dto.PlanCreateRequest;
import com.example.pmdaily.plan.dto.PlanResponse;
import com.example.pmdaily.plan.dto.PlanUpdateRequest;
import com.example.pmdaily.plan.mapper.PlanMapper;
import com.example.pmdaily.project.Project;
import com.example.pmdaily.project.ProjectMember;
import com.example.pmdaily.project.ProjectMemberRepository;
import com.example.pmdaily.project.ProjectMemberRole;
import com.example.pmdaily.project.ProjectRepository;
import com.example.pmdaily.security.UserPrincipal;

/**
 * Nghiệp vụ Project Plan (docs/api/13-planning-api.md muc 2.1, 3.1) — PLN-FR-PLAN-*.
 * Quyền: xem theo phạm vi dự án; thao tác quản lý yêu cầu PM của dự án hoặc ADMIN.
 */
@Service
@Transactional(readOnly = true)
public class PlanService {

    private static final Logger log = LoggerFactory.getLogger(PlanService.class);

    private static final List<PlanStatus> MASTER_ACTIVE_STATUSES =
            List.of(PlanStatus.APPROVED, PlanStatus.ACTIVE);

    private final ProjectPlanRepository planRepository;
    private final PlanVersionRepository versionRepository;
    private final PlanTaskRepository taskRepository;
    private final PlanCalendarRepository calendarRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final PlanMapper planMapper;
    private final AuditService auditService;

    public PlanService(
            ProjectPlanRepository planRepository,
            PlanVersionRepository versionRepository,
            PlanTaskRepository taskRepository,
            PlanCalendarRepository calendarRepository,
            ProjectRepository projectRepository,
            ProjectMemberRepository memberRepository,
            PlanMapper planMapper,
            AuditService auditService) {
        this.planRepository = planRepository;
        this.versionRepository = versionRepository;
        this.taskRepository = taskRepository;
        this.calendarRepository = calendarRepository;
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.planMapper = planMapper;
        this.auditService = auditService;
    }

    public PageResponse<PlanResponse> search(
            UserPrincipal actor,
            String keyword,
            UUID projectId,
            PlanType planType,
            PlanStatus status,
            int page,
            int size,
            String sortStr) {
        Pageable pageable = createPageable(page, size, sortStr);
        Specification<ProjectPlan> spec = Specification.where(PlanSpecification.notDeleted())
                .and(PlanSpecification.keyword(keyword))
                .and(PlanSpecification.projectId(projectId))
                .and(PlanSpecification.planType(planType))
                .and(PlanSpecification.status(status));

        if (!actor.getRoles().contains("ADMIN")) {
            spec = spec.and(PlanSpecification.memberOf(actor.getId()));
        }

        var planPage = planRepository.findAll(spec, pageable);
        return PageResponse.of(planPage, planMapper::toResponse);
    }

    public PlanResponse get(UUID id, UserPrincipal actor) {
        ProjectPlan plan = findActive(id);
        checkProjectViewAccess(actor, plan.getProject().getId());
        return planMapper.toResponse(plan);
    }

    @Transactional
    public PlanResponse create(UserPrincipal actor, PlanCreateRequest request) {
        Project project = projectRepository.findById(request.projectId())
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Dự án", request.projectId()));

        checkProjectManageAccess(actor, project.getId());

        if (planRepository.existsByProjectIdAndPlanCodeAndDeletedAtIsNull(
                project.getId(), request.planCode().trim())) {
            throw new ConflictException("planCode đã tồn tại trong dự án");
        }

        validateDateRange(request.plannedStart(), request.plannedFinish());
        ProjectPlan parent = resolveParent(project.getId(), request.planType(), request.parentPlanId());

        ProjectPlan plan = new ProjectPlan();
        plan.setProject(project);
        plan.setPlanCode(request.planCode().trim());
        plan.setPlanName(request.planName().trim());
        plan.setDescription(request.description());
        plan.setPlanType(request.planType());
        plan.setParentPlan(parent);
        plan.setCalendarId(resolveCalendarId(request.calendarId()));
        plan.setPlannedStart(request.plannedStart());
        plan.setPlannedFinish(request.plannedFinish());
        plan.setStatus(PlanStatus.DRAFT);
        plan.setProgress(0);
        plan.setParentMilestoneTaskId(request.parentMilestoneTaskId());

        ProjectPlan saved = planRepository.save(plan);
        PlanVersion version = new PlanVersion();
        version.setPlan(saved);
        version.setVersionNo(1);
        version.setStatus(PlanVersionStatus.ACTIVE);
        PlanVersion savedVersion = versionRepository.save(version);
        saved.setActiveVersion(savedVersion);
        saved = planRepository.saveAndFlush(saved);

        auditService.record("PLAN_CREATED", "PROJECT_PLAN", saved.getId(),
                Map.of("planCode", saved.getPlanCode(), "planType", saved.getPlanType(),
                        "projectId", saved.getProject().getId()));

        log.info("plan.create success id={} code={} type={} actor={}",
                saved.getId(), saved.getPlanCode(), saved.getPlanType(), actor.getUsername());
        return planMapper.toResponse(saved);
    }

    @Transactional
    public PlanResponse update(UserPrincipal actor, UUID id, PlanUpdateRequest request) {
        ProjectPlan plan = findActive(id);
        checkProjectManageAccess(actor, plan.getProject().getId());

        if (!Objects.equals(plan.getVersion(), request.version())) {
            throw new ConflictException("Record modified by another transaction");
        }

        validateDateRange(request.plannedStart(), request.plannedFinish());

        plan.setPlanName(request.planName().trim());
        plan.setDescription(request.description());
        plan.setCalendarId(resolveCalendarId(request.calendarId()));
        plan.setPlannedStart(request.plannedStart());
        plan.setPlannedFinish(request.plannedFinish());
        plan.setNote(request.note());

        ProjectPlan updated = planRepository.saveAndFlush(plan);
        auditService.record("PLAN_UPDATED", "PROJECT_PLAN", updated.getId(),
                Map.of("planCode", updated.getPlanCode(), "status", updated.getStatus()));

        log.info("plan.update success id={} actor={}", updated.getId(), actor.getUsername());
        return planMapper.toResponse(updated);
    }

    @Transactional
    public void delete(UserPrincipal actor, UUID id) {
        ProjectPlan plan = findActive(id);
        checkProjectManageAccess(actor, plan.getProject().getId());

        if (!planRepository.findByParentPlanIdAndDeletedAtIsNull(plan.getId()).isEmpty()) {
            throw new BusinessException(ErrorCode.HAS_CHILDREN,
                    "Kế hoạch còn Detail Plan con, không thể xóa");
        }

        plan.setDeletedAt(Instant.now());
        plan.setDeletedBy(actor.getId());
        planRepository.saveAndFlush(plan);

        auditService.record("PLAN_DELETED", "PROJECT_PLAN", plan.getId(),
                Map.of("planCode", plan.getPlanCode()));
        log.info("plan.delete success id={} actor={}", plan.getId(), actor.getUsername());
    }

    @Transactional
    public PlanResponse submit(UserPrincipal actor, UUID id) {
        ProjectPlan plan = findActive(id);
        checkProjectManageAccess(actor, plan.getProject().getId());

        assertTransition(plan, PlanStatus.DRAFT, PlanStatus.SUBMITTED);
        if (taskRepository.countByPlanIdAndDeletedAtIsNull(plan.getId()) == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Kế hoạch phải có ít nhất một planning task trước khi submit");
        }
        plan.setStatus(PlanStatus.SUBMITTED);
        ProjectPlan updated = planRepository.saveAndFlush(plan);

        auditService.record("PLAN_SUBMITTED", "PROJECT_PLAN", updated.getId(),
                Map.of("planCode", updated.getPlanCode()));
        log.info("plan.submit success id={} actor={}", updated.getId(), actor.getUsername());
        return planMapper.toResponse(updated);
    }

    @Transactional
    public PlanResponse approve(UserPrincipal actor, UUID id) {
        ProjectPlan plan = findActive(id);
        checkProjectManageAccess(actor, plan.getProject().getId());

        assertTransition(plan, PlanStatus.SUBMITTED, PlanStatus.APPROVED);
        assertSingleActiveMaster(plan);

        plan.setStatus(PlanStatus.APPROVED);
        ProjectPlan updated = planRepository.saveAndFlush(plan);

        auditService.record("PLAN_APPROVED", "PROJECT_PLAN", updated.getId(),
                Map.of("planCode", updated.getPlanCode()));
        log.info("Approved success id={} actor={}", updated.getId(), actor.getUsername());
        return planMapper.toResponse(updated);
    }

    @Transactional
    public PlanResponse activate(UserPrincipal actor, UUID id) {
        ProjectPlan plan = findActive(id);
        checkProjectManageAccess(actor, plan.getProject().getId());

        assertTransition(plan, PlanStatus.APPROVED, PlanStatus.ACTIVE);
        assertSingleActiveMaster(plan);

        plan.setStatus(PlanStatus.ACTIVE);
        ProjectPlan updated = planRepository.saveAndFlush(plan);

        auditService.record("PLAN_ACTIVATED", "PROJECT_PLAN", updated.getId(),
                Map.of("planCode", updated.getPlanCode()));
        log.info("success id={} actor={}", updated.getId(), actor.getUsername());
        return planMapper.toResponse(updated);
    }

    private ProjectPlan resolveParent(UUID projectId, PlanType type, UUID parentPlanId) {
        if (type == PlanType.DETAIL) {
            if (parentPlanId == null) {
                throw new BusinessException(ErrorCode.INVALID_PARENT_PLAN,
                        "Detail Plan bắt buộc có parentPlanId trỏ Master Plan");
            }
            ProjectPlan parent = findActive(parentPlanId);
            if (!Objects.equals(parent.getProject().getId(), projectId)) {
                throw new BusinessException(ErrorCode.INVALID_PARENT_PLAN, "Kế hoạch cha phải cùng dự án");
            }
            if (parent.getPlanType() != PlanType.MASTER) {
                throw new BusinessException(ErrorCode.INVALID_PARENT_PLAN,
                        "Cha của Detail Plan phải là Master Plan");
            }
            return parent;
        }
        if (parentPlanId != null) {
            throw new BusinessException(ErrorCode.INVALID_PARENT_PLAN,
                    "Chỉ Detail Plan mới có kế hoạch cha");
        }
        return null;
    }

    private void assertSingleActiveMaster(ProjectPlan plan) {
        if (plan.getPlanType() != PlanType.MASTER) {
            return;
        }
        long count = planRepository.countByProjectIdAndPlanTypeAndStatusInAndDeletedAtIsNullAndIdNot(
                plan.getProject().getId(), PlanType.MASTER, MASTER_ACTIVE_STATUSES, plan.getId());
        if (count > 0) {
            throw new ConflictException("Dự án đã có Master Plan ở trạng thái APPROVED/ACTIVE");
        }
    }

    private void assertTransition(ProjectPlan plan, PlanStatus from, PlanStatus to) {
        if (plan.getStatus() != from) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Không thể chuyển trạng thái " + plan.getStatus() + " → " + to);
        }
    }

    private void validateDateRange(LocalDate start, LocalDate finish) {
        if (start != null && finish != null && finish.isBefore(start)) {
            throw new BusinessException(ErrorCode.INVALID_DATE_RANGE);
        }
    }

    private ProjectPlan findActive(UUID id) {
        return planRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Kế hoạch", id));
    }

    private UUID resolveCalendarId(UUID calendarId) {
        if (calendarId == null) {
            return null;
        }
        return calendarRepository.findByIdAndDeletedAtIsNull(calendarId)
                .map(PlanCalendar::getId)
                .orElseThrow(() -> new ResourceNotFoundException("Working calendar", calendarId));
    }

    private void checkProjectViewAccess(UserPrincipal actor, UUID projectId) {
        if (actor.getRoles().contains("ADMIN")) {
            return;
        }
        if (!memberRepository.existsByProjectIdAndUser_Id(projectId, actor.getId())) {
            throw new AccessDeniedException("Access denied to project");
        }
    }

    private void checkProjectManageAccess(UserPrincipal actor, UUID projectId) {
        if (actor.getRoles().contains("ADMIN")) {
            return;
        }
        ProjectMember member = memberRepository.findByProjectIdAndUser_Id(projectId, actor.getId())
                .orElseThrow(() -> new AccessDeniedException("Access denied to project"));
        if (member.getRole() != ProjectMemberRole.PROJECT_MANAGER) {
            throw new AccessDeniedException("Cần quyền PROJECT_MANAGER của dự án");
        }
    }

    private Pageable createPageable(int page, int size, String sortStr) {
        if (sortStr == null || sortStr.isBlank()) {
            return PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));
        }
        String[] parts = sortStr.split(",");
        String property = parts[0].trim();
        Sort.Direction direction = (parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim()))
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return PageRequest.of(page, size, Sort.by(direction, property));
    }
}