package com.example.pmdaily.actionitem;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

import com.example.pmdaily.actionitem.dto.ActionItemCreateRequest;
import com.example.pmdaily.actionitem.dto.ActionItemResponse;
import com.example.pmdaily.actionitem.dto.ActionItemUpdateRequest;
import com.example.pmdaily.actionitem.dto.ConvertToTaskRequest;
import com.example.pmdaily.actionitem.mapper.ActionItemMapper;
import com.example.pmdaily.audit.AuditService;
import com.example.pmdaily.common.ErrorCode;
import com.example.pmdaily.common.PageResponse;
import com.example.pmdaily.exception.BusinessException;
import com.example.pmdaily.exception.ConflictException;
import com.example.pmdaily.exception.ResourceNotFoundException;
import com.example.pmdaily.meeting.Meeting;
import com.example.pmdaily.meeting.MeetingRepository;
import com.example.pmdaily.project.Project;
import com.example.pmdaily.project.ProjectMemberRepository;
import com.example.pmdaily.project.ProjectMemberRole;
import com.example.pmdaily.project.ProjectRepository;
import com.example.pmdaily.security.UserPrincipal;
import com.example.pmdaily.task.Task;
import com.example.pmdaily.task.TaskRepository;
import com.example.pmdaily.task.TaskService;
import com.example.pmdaily.task.TaskSource;
import com.example.pmdaily.task.dto.TaskCreateRequest;
import com.example.pmdaily.task.dto.TaskResponse;
import com.example.pmdaily.task.dto.UserBriefResponse;
import com.example.pmdaily.user.User;
import com.example.pmdaily.user.UserRepository;
import com.example.pmdaily.user.UserStatus;

/**
 * Nghiệp vụ action item (docs/api/07-action-item-api.md, UC-007, BR-AI-01..04).
 * Kiểm tra kép: quyền toàn cục (controller @PreAuthorize) + membership/PM dự án tại service.
 * Notification ACTION_ITEM_ASSIGNED (hậu điều kiện tạo AI) được ghi nhận khi module
 * Notification triển khai — xem docs/build/environment-check.md muc 10.
 */
@Service
public class ActionItemService {

    private static final Logger log = LoggerFactory.getLogger(ActionItemService.class);
    private static final String ENTITY_TYPE = "ACTION_ITEM";
    private static final List<String> SORT_WHITELIST =
            List.of("title", "dueDate", "priority", "status", "createdAt");

    private final ActionItemRepository actionItemRepository;
    private final MeetingRepository meetingRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final TaskService taskService;
    private final TaskRepository taskRepository;
    private final ActionItemMapper mapper;
    private final AuditService auditService;

    public ActionItemService(ActionItemRepository actionItemRepository,
            MeetingRepository meetingRepository,
            ProjectRepository projectRepository,
            ProjectMemberRepository memberRepository,
            UserRepository userRepository,
            TaskService taskService,
            TaskRepository taskRepository,
            ActionItemMapper mapper,
            AuditService auditService) {
        this.actionItemRepository = actionItemRepository;
        this.meetingRepository = meetingRepository;
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.taskService = taskService;
        this.taskRepository = taskRepository;
        this.mapper = mapper;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------ CRUD

    @Transactional
    public ActionItemResponse create(UserPrincipal actor, ActionItemCreateRequest request) {
        Meeting meeting = findActiveMeeting(request.meetingId());
        Project project = findActiveProject(request.projectId());
        if (!meeting.getProject().getId().equals(project.getId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Action item phải thuộc cùng dự án với cuộc họp (BR-AI-01)");
        }
        ensureCanManage(project, actor);

        ActionItem item = new ActionItem();
        item.setMeeting(meeting);
        item.setProject(project);
        item.setTitle(request.title().trim());
        item.setDescription(request.description());
        item.setAssignee(findProjectMemberUser(project, request.assigneeId()));
        item.setDueDate(request.dueDate());
        item.setPriority(request.priority() != null ? request.priority() : com.example.pmdaily.task.TaskPriority.MEDIUM);
        item.setStatus(ActionItemStatus.OPEN);
        item.setProgress(0);
        ActionItem saved = actionItemRepository.save(item);

        auditService.record("ACTION_ITEM_CREATED", ENTITY_TYPE, saved.getId(),
                Map.of("title", saved.getTitle(), "meetingId", meeting.getId().toString()));
        log.info("action-item.create success id={} title={} actor={}", saved.getId(), saved.getTitle(),
                actor.getUsername());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ActionItemResponse get(UUID id, UserPrincipal actor) {
        ActionItem item = findActive(id);
        ensureCanView(item.getProject(), actor);
        return toResponse(item);
    }

    @Transactional(readOnly = true)
    public PageResponse<ActionItemResponse> search(UserPrincipal actor,
            String keyword, UUID projectId, UUID meetingId, ActionItemStatus status, UUID assigneeId,
            Boolean overdue, int page, int size, String sort) {
        validatePagination(page, size);
        Pageable pageable = PageRequest.of(page, size, resolveSort(sort));
        Specification<ActionItem> spec = Specification.where(ActionItemSpecification.notDeleted())
                .and(ActionItemSpecification.keyword(keyword))
                .and(ActionItemSpecification.projectId(projectId))
                .and(ActionItemSpecification.meetingId(meetingId))
                .and(ActionItemSpecification.statuses(status))
                .and(ActionItemSpecification.assigneeId(assigneeId))
                .and(ActionItemSpecification.overdue(
                        Boolean.TRUE.equals(overdue) ? LocalDate.now() : null));
        if (!isAdminOrPm(actor)) {
            if (projectId != null && !isMember(projectId, actor.getId())) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }
            spec = spec.and(ActionItemSpecification.memberOf(actor.getId()));
        }
        Page<ActionItem> result = actionItemRepository.findAll(spec, pageable);
        return PageResponse.of(result, this::toResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<ActionItemResponse> overdue(UserPrincipal actor, int page, int size, String sort) {
        validatePagination(page, size);
        Pageable pageable = PageRequest.of(page, size, resolveSort(sort));
        Specification<ActionItem> spec = Specification.where(ActionItemSpecification.notDeleted())
                .and(ActionItemSpecification.overdue(LocalDate.now()));
        if (!isAdminOrPm(actor)) {
            spec = spec.and(ActionItemSpecification.memberOf(actor.getId()));
        }
        Page<ActionItem> result = actionItemRepository.findAll(spec, pageable);
        log.info("action-item.overdue count={} actor={}", result.getTotalElements(), actor.getUsername());
        return PageResponse.of(result, this::toResponse);
    }

    @Transactional
    public ActionItemResponse update(UserPrincipal actor, UUID id, ActionItemUpdateRequest request) {
        ActionItem item = findActive(id);
        boolean manager = isAdminOrPm(actor) || isProjectManager(item.getProject(), actor.getId());
        boolean assignee = item.getAssignee() != null && item.getAssignee().getId().equals(actor.getId());
        if (!manager && !assignee) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        if (item.getVersion() != request.version()) {
            throw new ConflictException();
        }

        if (manager) {
            if (request.title() != null) {
                item.setTitle(request.title().trim());
            }
            if (request.description() != null) {
                item.setDescription(request.description());
            }
            if (request.dueDate() != null) {
                item.setDueDate(request.dueDate());
            }
            if (request.priority() != null) {
                item.setPriority(request.priority());
            }
        }
        if (request.status() != null) {
            if (item.getStatus() == ActionItemStatus.DONE
                    && request.status() != ActionItemStatus.DONE) {
                throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                        "Action item đã đóng không thể mở lại");
            }
            item.setStatus(request.status());
            if (request.status() == ActionItemStatus.DONE) {
                item.setProgress(100);
            }
        }
        if (request.progress() != null) {
            if (item.getStatus() == ActionItemStatus.DONE && request.progress() < 100) {
                throw new BusinessException(ErrorCode.PROGRESS_REQUIRED_FOR_DONE);
            }
            item.setProgress(request.progress());
        }

        ActionItem saved = actionItemRepository.save(item);
        auditService.record("ACTION_ITEM_UPDATED", ENTITY_TYPE, saved.getId(),
                Map.of("title", saved.getTitle(), "status", saved.getStatus().name()));
        log.info("action-item.update success id={} actor={}", saved.getId(), actor.getUsername());
        return toResponse(saved);
    }

    @Transactional
    public void delete(UserPrincipal actor, UUID id) {
        ActionItem item = findActive(id);
        ensureCanManage(item.getProject(), actor);
        item.setDeletedAt(Instant.now());
        item.setDeletedBy(actor.getId());
        actionItemRepository.save(item);
        auditService.record("ACTION_ITEM_DELETED", ENTITY_TYPE, id,
                Map.of("title", item.getTitle()));
        log.info("action-item.delete success id={} actor={}", id, actor.getUsername());
    }

    // --------------------------------------------------------- convert to task

    @Transactional
    public TaskResponse convertToTask(UserPrincipal actor, UUID id, ConvertToTaskRequest request) {
        ActionItem item = findActive(id);
        ensureCanManage(item.getProject(), actor);
        if (item.getLinkedTask() != null) {
            throw new BusinessException(ErrorCode.ALREADY_LINKED,
                    "Action item đã được chuyển thành công việc (BR-AI-04)");
        }
        if (item.getStatus() == ActionItemStatus.DONE
                || item.getStatus() == ActionItemStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Action item đã đóng/hủy không thể chuyển thành công việc");
        }

        com.example.pmdaily.task.TaskPriority priority = request.priority() != null
                ? request.priority()
                : item.getPriority();
        LocalDate dueDate = request.dueDate() != null ? request.dueDate() : item.getDueDate();
        TaskCreateRequest taskRequest = new TaskCreateRequest(
                item.getProject().getId(),
                null,
                item.getTitle(),
                item.getDescription(),
                item.getAssignee().getId(),
                List.of(),
                List.of(),
                null,
                priority,
                null,
                TaskSource.ACTION_ITEM,
                null,
                dueDate,
                0,
                null,
                null,
                null,
                null,
                null,
                List.of());
        TaskResponse task = taskService.create(actor, taskRequest);

        item.setLinkedTask(taskRepository.getReferenceById(task.id()));
        actionItemRepository.save(item);
        auditService.record("ACTION_ITEM_CONVERTED", ENTITY_TYPE, item.getId(),
                Map.of("taskId", task.id().toString(), "taskCode", task.code()));
        log.info("action-item.convert success id={} taskId={} actor={}", item.getId(), task.id(),
                actor.getUsername());
        return task;
    }

    // ---------------------------------------------------------------- helpers

    private ActionItemResponse toResponse(ActionItem item) {
        Map<UUID, User> users = loadUsers(List.of(item.getAssignee().getId()));
        UserBriefResponse assignee = mapper.toUserBrief(users.get(item.getAssignee().getId()));
        return mapper.toResponse(item, assignee);
    }

    private Map<UUID, User> loadUsers(List<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }

    private ActionItem findActive(UUID id) {
        return actionItemRepository.findById(id)
                .filter(i -> i.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("action item", id));
    }

    private Meeting findActiveMeeting(UUID meetingId) {
        return meetingRepository.findById(meetingId)
                .filter(m -> m.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("cuộc họp", meetingId));
    }

    private Project findActiveProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("dự án", projectId));
    }

    private User findProjectMemberUser(Project project, UUID userId) {
        User user = userRepository.findById(userId)
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("tài khoản", userId));
        if (!memberRepository.existsByProjectIdAndUser_Id(project.getId(), userId)) {
            throw new BusinessException(ErrorCode.NOT_PROJECT_MEMBER);
        }
        return user;
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

    private boolean isAdminOrPm(UserPrincipal actor) {
        return actor.getRoles().contains("ADMIN") || actor.getRoles().contains("PROJECT_MANAGER");
    }

    private boolean isProjectManager(Project project, UUID userId) {
        return memberRepository.findByProjectIdAndUser_Id(project.getId(), userId)
                .filter(m -> m.getRole() == ProjectMemberRole.PROJECT_MANAGER)
                .isPresent();
    }

    private boolean isMember(UUID projectId, UUID userId) {
        return memberRepository.existsByProjectIdAndUser_Id(projectId, userId);
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
}
