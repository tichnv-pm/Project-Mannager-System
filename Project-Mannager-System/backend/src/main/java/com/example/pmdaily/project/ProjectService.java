package com.example.pmdaily.project;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.pmdaily.audit.AuditService;
import com.example.pmdaily.common.ErrorCode;
import com.example.pmdaily.common.PageResponse;
import com.example.pmdaily.exception.BusinessException;
import com.example.pmdaily.exception.ConflictException;
import com.example.pmdaily.exception.ResourceNotFoundException;
import com.example.pmdaily.project.dto.ProjectCreateRequest;
import com.example.pmdaily.project.dto.ProjectMemberRequest;
import com.example.pmdaily.project.dto.ProjectMemberResponse;
import com.example.pmdaily.project.dto.ProjectMemberRoleRequest;
import com.example.pmdaily.project.dto.ProjectResponse;
import com.example.pmdaily.project.dto.ProjectUpdateRequest;
import com.example.pmdaily.project.mapper.ProjectMapper;
import com.example.pmdaily.security.UserPrincipal;
import com.example.pmdaily.user.User;
import com.example.pmdaily.user.UserRepository;
import com.example.pmdaily.user.UserStatus;

/**
 * Nghiệp vụ dự án & thành viên (docs/api/04-project-api.md, UC-003, UC-004).
 * Kiểm tra kép: quyền toàn cục (đã làm ở controller qua @PreAuthorize) + membership/PM dự án tại service.
 */
@Service
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);
    private static final String ENTITY_TYPE = "PROJECT";
    private static final List<String> SORT_WHITELIST =
            List.of("code", "name", "status", "startDate", "endDate", "createdAt", "progress");

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final ProjectMapper projectMapper;
    private final AuditService auditService;

    public ProjectService(ProjectRepository projectRepository,
            ProjectMemberRepository memberRepository,
            UserRepository userRepository,
            ProjectMapper projectMapper,
            AuditService auditService) {
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.projectMapper = projectMapper;
        this.auditService = auditService;
    }

    @Transactional
    public ProjectResponse create(UserPrincipal actor, ProjectCreateRequest request) {
        validateDates(request.startDate(), request.endDate());
        if (projectRepository.existsByCode(request.code())) {
            throw new BusinessException(ErrorCode.DUPLICATE, "Mã dự án đã tồn tại");
        }

        Project project = projectMapper.toEntity(request);
        if (project.getStatus() == null) {
            project.setStatus(ProjectStatus.PLANNING);
        }
        if (request.projectManagerId() != null) {
            User manager = findActiveUser(request.projectManagerId());
            project.setProjectManager(manager);
        }
        Project saved = projectRepository.save(project);

        if (request.projectManagerId() != null) {
            addMemberInternal(saved, request.projectManagerId(), ProjectMemberRole.PROJECT_MANAGER);
        }

        auditService.record("PROJECT_CREATED", ENTITY_TYPE, saved.getId(),
                Map.of("code", saved.getCode(), "name", saved.getName()));
        log.info("project.create success id={} code={} actor={}", saved.getId(), saved.getCode(), actor.getUsername());
        return toResponse(saved, memberCount(saved.getId()));
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(UUID projectId, UserPrincipal actor) {
        Project project = findActive(projectId);
        ensureCanView(project, actor);
        return toResponse(project, memberCount(projectId));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProjectResponse> search(UUID actorId, List<String> actorRoles,
            String keyword, ProjectStatus status, UUID projectManagerId, boolean myOnly,
            int page, int size, String sort) {
        validatePagination(page, size);
        Sort resolvedSort = resolveSort(sort);
        Pageable pageable = PageRequest.of(page, size, resolvedSort);

        Specification<Project> spec = Specification.where(ProjectSpecification.notDeleted())
                .and(ProjectSpecification.keyword(keyword))
                .and(ProjectSpecification.status(status))
                .and(ProjectSpecification.projectManager(projectManagerId));

        boolean isAdminOrPm = actorRoles.contains("ADMIN") || actorRoles.contains("PROJECT_MANAGER");
        if (myOnly || !isAdminOrPm) {
            spec = spec.and(ProjectSpecification.memberOf(actorId));
        }

        Page<Project> result = projectRepository.findAll(spec, pageable);
        Map<UUID, Long> memberCounts = memberCountsByProjects(result.getContent());
        return PageResponse.of(result, p -> toResponse(p, memberCounts.getOrDefault(p.getId(), 0L)));
    }

    @Transactional
    public ProjectResponse update(UserPrincipal actor, UUID projectId, ProjectUpdateRequest request) {
        Project project = findActive(projectId);
        ensureCanManage(project, actor);
        validateDates(request.startDate(), request.endDate());

        if (project.getVersion() != request.version()) {
            throw new ConflictException();
        }

        if (!project.getCode().equals(request.code())
                && projectRepository.existsByCode(request.code())) {
            throw new BusinessException(ErrorCode.DUPLICATE, "Mã dự án đã tồn tại");
        }

        project.setCode(request.code());
        project.setName(request.name());
        project.setDescription(request.description());
        project.setStatus(request.status() != null ? request.status() : project.getStatus());
        project.setStartDate(request.startDate());
        project.setEndDate(request.endDate());
        project.setCustomerName(request.customerName());
        project.setNote(request.note());

        if (request.projectManagerId() != null) {
            User newManager = findActiveUser(request.projectManagerId());
            project.setProjectManager(newManager);
            memberRepository.findByProjectIdAndUser_Id(projectId, newManager.getId())
                    .ifPresentOrElse(
                            member -> {
                                if (member.getRole() != ProjectMemberRole.PROJECT_MANAGER) {
                                    member.setRole(ProjectMemberRole.PROJECT_MANAGER);
                                    memberRepository.save(member);
                                }
                            },
                            () -> addMemberInternal(project, newManager.getId(),
                                    ProjectMemberRole.PROJECT_MANAGER));
        }

        Project saved = projectRepository.save(project);
        auditService.record("PROJECT_UPDATED", ENTITY_TYPE, saved.getId(),
                Map.of("code", saved.getCode(), "name", saved.getName()));
        log.info("project.update success id={} actor={}", saved.getId(), actor.getUsername());
        return toResponse(saved, memberCount(saved.getId()));
    }

    @Transactional
    public void delete(UserPrincipal actor, UUID projectId, boolean confirm) {
        Project project = findActive(projectId);
        ensureCanManage(project, actor);

        long openTasks = projectRepository.countOpenTasks(projectId);
        if (project.getStatus() == ProjectStatus.ACTIVE && openTasks > 0 && !confirm) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Dự án ACTIVE có " + openTasks + " công việc chưa đóng, cần xác nhận xóa");
        }

        project.setDeletedAt(java.time.Instant.now());
        project.setDeletedBy(actor.getId());
        projectRepository.save(project);
        auditService.record("PROJECT_DELETED", ENTITY_TYPE, projectId,
                Map.of("code", project.getCode()));
        log.info("project.delete success id={} actor={}", projectId, actor.getUsername());
    }

    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> listMembers(UUID projectId, UserPrincipal actor) {
        Project project = findActive(projectId);
        ensureCanView(project, actor);
        return memberRepository.findByProjectIdOrderByCreatedAtAsc(projectId).stream()
                .map(projectMapper::toMemberResponse)
                .toList();
    }

    @Transactional
    public ProjectMemberResponse addMember(UserPrincipal actor, UUID projectId, ProjectMemberRequest request) {
        Project project = findActive(projectId);
        ensureCanManage(project, actor);
        if (memberRepository.existsByProjectIdAndUser_Id(projectId, request.userId())) {
            throw new BusinessException(ErrorCode.DUPLICATE, "Thành viên đã tồn tại trong dự án");
        }
        findActiveUser(request.userId());
        ProjectMember member = addMemberInternal(project, request.userId(), request.role());
        auditService.record("PROJECT_MEMBER_ADDED", ENTITY_TYPE, projectId,
                Map.of("userId", request.userId(), "role", request.role().name()));
        log.info("project.member.added projectId={} userId={} actor={}",
                projectId, request.userId(), actor.getUsername());
        return projectMapper.toMemberResponse(member);
    }

    @Transactional
    public ProjectMemberResponse changeRole(UserPrincipal actor, UUID projectId, UUID userId,
            ProjectMemberRoleRequest request) {
        Project project = findActive(projectId);
        ensureCanManage(project, actor);
        ProjectMember member = memberRepository.findByProjectIdAndUser_Id(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("thành viên dự án", userId));

        if (member.getRole() == ProjectMemberRole.PROJECT_MANAGER
                && request.role() != ProjectMemberRole.PROJECT_MANAGER
                && memberRepository.countByProjectIdAndRole(projectId, ProjectMemberRole.PROJECT_MANAGER) == 1) {
            throw new BusinessException(ErrorCode.PROJECT_MANAGER_REQUIRED);
        }

        member.setRole(request.role());
        memberRepository.save(member);
        auditService.record("PROJECT_MEMBER_ROLE_CHANGED", ENTITY_TYPE, projectId,
                Map.of("userId", userId, "role", request.role().name()));
        log.info("project.member.role projectId={} userId={} role={} actor={}",
                projectId, userId, request.role(), actor.getUsername());
        return projectMapper.toMemberResponse(member);
    }

    @Transactional
    public void removeMember(UserPrincipal actor, UUID projectId, UUID userId) {
        Project project = findActive(projectId);
        ensureCanManage(project, actor);
        ProjectMember member = memberRepository.findByProjectIdAndUser_Id(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("thành viên dự án", userId));

        if (member.getRole() == ProjectMemberRole.PROJECT_MANAGER
                && memberRepository.countByProjectIdAndRole(projectId, ProjectMemberRole.PROJECT_MANAGER) == 1) {
            throw new BusinessException(ErrorCode.PROJECT_MANAGER_REQUIRED);
        }

        memberRepository.delete(member);
        auditService.record("PROJECT_MEMBER_REMOVED", ENTITY_TYPE, projectId,
                Map.of("userId", userId));
        log.info("project.member.removed projectId={} userId={} actor={}",
                projectId, userId, actor.getUsername());
    }

    // ---------------------------------------------------------------- helpers

    private ProjectMember addMemberInternal(Project project, UUID userId, ProjectMemberRole role) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("tài khoản", userId));
        ProjectMember member = new ProjectMember();
        member.setProject(project);
        member.setUser(user);
        member.setRole(role);
        return memberRepository.save(member);
    }

    private Project findActive(UUID projectId) {
        return projectRepository.findById(projectId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("dự án", projectId));
    }

    private User findActiveUser(UUID userId) {
        return userRepository.findById(userId)
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("tài khoản", userId));
    }

    private void ensureCanView(Project project, UserPrincipal actor) {
        if (isAdmin(actor) || isMember(project.getId(), actor.getId())) {
            return;
        }
        throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }

    private void ensureCanManage(Project project, UserPrincipal actor) {
        if (isAdmin(actor)) {
            return;
        }
        if (memberRepository.findByProjectIdAndUser_Id(project.getId(), actor.getId())
                .filter(m -> m.getRole() == ProjectMemberRole.PROJECT_MANAGER)
                .isPresent()) {
            return;
        }
        throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }

    private boolean isAdmin(UserPrincipal actor) {
        return actor.getRoles().contains("ADMIN");
    }

    private boolean isMember(UUID projectId, UUID userId) {
        return memberRepository.existsByProjectIdAndUser_Id(projectId, userId);
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new BusinessException(ErrorCode.INVALID_DATE_RANGE,
                    "Ngày kết thúc không được nhỏ hơn ngày bắt đầu");
        }
    }

    private void validatePagination(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "page phải >= 0 và size phải trong khoảng 1–100");
        }
    }

    private Sort resolveSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        if (!SORT_WHITELIST.contains(field)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Trường sắp xếp không hợp lệ: " + field);
        }
        Sort.Direction direction = parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc")
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }

    private long memberCount(UUID projectId) {
        return memberRepository.countByProjectId(projectId);
    }

    private Map<UUID, Long> memberCountsByProjects(List<Project> projects) {
        if (projects.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = projects.stream().map(Project::getId).toList();
        return memberRepository.countGroupByProjectIds(ids).stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> (Long) row[1]));
    }

    private ProjectResponse toResponse(Project project, long memberCount) {
        ProjectResponse response = projectMapper.toResponse(project);
        return new ProjectResponse(
                response.id(), response.code(), response.name(), response.description(),
                response.status(), response.startDate(), response.endDate(),
                response.projectManagerId(), response.customerName(), response.progress(),
                memberCount, response.createdAt(), response.version());
    }
}
