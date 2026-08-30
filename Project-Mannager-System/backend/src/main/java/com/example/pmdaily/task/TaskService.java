package com.example.pmdaily.task;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.pmdaily.audit.AuditLog;
import com.example.pmdaily.audit.AuditLogRepository;
import com.example.pmdaily.audit.AuditService;
import com.example.pmdaily.common.ErrorCode;
import com.example.pmdaily.sprint.SprintRepository;
import com.example.pmdaily.common.PageResponse;
import com.example.pmdaily.exception.BusinessException;
import com.example.pmdaily.exception.ConflictException;
import com.example.pmdaily.exception.ResourceNotFoundException;
import com.example.pmdaily.project.Project;
import com.example.pmdaily.project.ProjectMember;
import com.example.pmdaily.project.ProjectMemberRepository;
import com.example.pmdaily.project.ProjectMemberRole;
import com.example.pmdaily.project.ProjectRepository;
import com.example.pmdaily.security.UserPrincipal;
import com.example.pmdaily.task.dto.AttachmentResponse;
import com.example.pmdaily.task.dto.BlockerUpdateRequest;
import com.example.pmdaily.task.dto.CommentRequest;
import com.example.pmdaily.task.dto.CommentResponse;
import com.example.pmdaily.task.dto.ProgressUpdateRequest;
import com.example.pmdaily.task.dto.StatusUpdateRequest;
import com.example.pmdaily.task.dto.TagBriefResponse;
import com.example.pmdaily.task.dto.TagIdsRequest;
import com.example.pmdaily.task.dto.TaskCreateRequest;
import com.example.pmdaily.task.dto.TaskHistoryEntry;
import com.example.pmdaily.task.dto.TaskResponse;
import com.example.pmdaily.task.dto.TaskSummaryResponse;
import com.example.pmdaily.task.dto.TaskUpdateRequest;
import com.example.pmdaily.task.dto.UserBriefResponse;
import com.example.pmdaily.task.dto.UserIdsRequest;
import com.example.pmdaily.task.mapper.TaskMapper;
import com.example.pmdaily.user.User;
import com.example.pmdaily.user.UserRepository;
import com.example.pmdaily.user.UserStatus;

/**
 * Nghiệp vụ công việc (docs/api/05-task-api.md, UC-005, BR-TASK).
 * Kiểm tra kép: quyền toàn cục (controller @PreAuthorize) + membership/PM dự án tại service.
 */
@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);
    private static final String ENTITY_TYPE = "TASK";
    private static final List<String> SORT_WHITELIST =
            List.of("code", "title", "status", "priority", "dueDate", "progress", "createdAt", "updatedAt");
    private static final long MAX_EXPORT_ROWS = 10_000L;
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> MIME_WHITELIST = Set.of(
            "image/png", "image/jpg", "image/jpeg", "image/gif",
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain");

    private final TaskRepository taskRepository;
    private final TagRepository tagRepository;
    private final TaskAssigneeRepository assigneeRepository;
    private final TaskWatcherRepository watcherRepository;
    private final TaskTagRepository taskTagRepository;
    private final TaskCommentRepository commentRepository;
    private final AttachmentRepository attachmentRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final TaskCodeGenerator codeGenerator;
    private final TaskMapper taskMapper;
    private final SprintRepository sprintRepository;
    private final AuditService auditService;

    @Value("${app.storage.path:./uploads}")
    private String storagePath;

    public TaskService(TaskRepository taskRepository,
            TagRepository tagRepository,
            TaskAssigneeRepository assigneeRepository,
            TaskWatcherRepository watcherRepository,
            TaskTagRepository taskTagRepository,
            TaskCommentRepository commentRepository,
            AttachmentRepository attachmentRepository,
            ProjectRepository projectRepository,
            ProjectMemberRepository memberRepository,
            UserRepository userRepository,
            AuditLogRepository auditLogRepository,
            TaskCodeGenerator codeGenerator,
            TaskMapper taskMapper,
            SprintRepository sprintRepository,
            AuditService auditService) {
        this.taskRepository = taskRepository;
        this.tagRepository = tagRepository;
        this.assigneeRepository = assigneeRepository;
        this.watcherRepository = watcherRepository;
        this.taskTagRepository = taskTagRepository;
        this.commentRepository = commentRepository;
        this.attachmentRepository = attachmentRepository;
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.codeGenerator = codeGenerator;
        this.taskMapper = taskMapper;
        this.sprintRepository = sprintRepository;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------ CRUD

    @Transactional
    public TaskResponse create(UserPrincipal actor, TaskCreateRequest request) {
        Project project = findActiveProject(request.projectId());
        ensureCanView(project, actor);

        Task task = new Task();
        task.setProject(project);
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setReporter(findActiveUser(actor.getId()));
        task.setStatus(request.status() != null ? request.status() : TaskStatus.TODO);
        task.setPriority(request.priority() != null ? request.priority() : TaskPriority.MEDIUM);
        task.setType(request.type() != null ? request.type() : TaskType.TASK);
        task.setSource(request.source() != null ? request.source() : TaskSource.MANUAL);
        task.setStartDate(request.startDate());
        task.setDueDate(request.dueDate());
        task.setEstimateMinutes(request.estimateMinutes());
        task.setEstimateUnit(request.estimateUnit() != null ? request.estimateUnit() : TimeUnit.MINUTE);
        task.setNotes(request.notes());

        applyParent(task, request.parentTaskId(), actor);
        validateDates(task);
        task.setProgress(request.progress() != null ? request.progress() : 0);
        applyStatusRules(task, task.getStatus(), request.blocked(), request.blockerReason());
        if (request.assigneeId() != null) {
            task.setAssignee(findProjectMemberUser(project, request.assigneeId()));
        }
        if (request.sprintId() != null) {
            sprintRepository.findByIdAndDeletedAtIsNull(request.sprintId())
                    .filter(s -> s.getProjectId().equals(project.getId()))
                    .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "Sprint không hợp lệ cho dự án này"));
            task.setSprintId(request.sprintId());
        }

        String code = codeGenerator.nextCode(project.getId(), project.getCode());
        task.setCode(code);
        Task saved = taskRepository.save(task);

        List<UUID> collaborators = request.collaboratorIds() != null ? request.collaboratorIds() : List.of();
        List<UUID> watchers = request.watcherIds() != null ? request.watcherIds() : List.of();
        replaceCollaboratorsInternal(saved, collaborators, actor);
        replaceWatchersInternal(saved, watchers, actor);
        replaceTagsInternal(saved, request.tagIds() != null ? request.tagIds() : List.of(), actor);

        auditService.record("TASK_CREATED", ENTITY_TYPE, saved.getId(),
                Map.of("code", saved.getCode(), "title", saved.getTitle()));
        log.info("task.create success id={} code={} actor={}", saved.getId(), saved.getCode(),
                actor.getUsername());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public TaskResponse get(UUID taskId, UserPrincipal actor) {
        Task task = findActive(taskId);
        ensureCanView(task.getProject(), actor);
        return toResponse(task);
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskSummaryResponse> search(UserPrincipal actor,
            String keyword, UUID projectId, UUID assigneeId, List<TaskStatus> statuses,
            List<TaskPriority> priorities, List<TaskType> types, UUID tagId,
            LocalDate startDateFrom, LocalDate startDateTo,
            LocalDate dueDateFrom, LocalDate dueDateTo,
            Boolean overdue, Boolean blocked,
            String sprintId,
            int page, int size, String sort) {
        validatePagination(page, size);
        Sort resolvedSort = resolveSort(sort);
        Pageable pageable = PageRequest.of(page, size, resolvedSort);

        Specification<Task> spec = Specification.where(TaskSpecification.notDeleted())
                .and(TaskSpecification.keyword(keyword))
                .and(TaskSpecification.projectId(projectId))
                .and(TaskSpecification.assigneeId(assigneeId))
                .and(TaskSpecification.statuses(statuses))
                .and(TaskSpecification.priorities(priorities))
                .and(TaskSpecification.types(types))
                .and(TaskSpecification.tagId(tagId))
                .and(TaskSpecification.startDateRange(startDateFrom, startDateTo))
                .and(TaskSpecification.dueDateRange(dueDateFrom, dueDateTo))
                .and(TaskSpecification.overdue(overdue != null && overdue ? LocalDate.now() : null))
                .and(blocked == null ? null : TaskSpecification.blocked(blocked))
                .and(TaskSpecification.sprintId(sprintId));

        if (!isAdminOrPm(actor)) {
            if (projectId != null && !isMember(projectId, actor.getId())) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }
            spec = spec.and(TaskSpecification.memberOf(actor.getId()));
        }

        Page<Task> result = taskRepository.findAll(spec, pageable);
        return PageResponse.of(result, taskMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskSummaryResponse> myTasks(UserPrincipal actor, UUID projectId,
            int page, int size, String sort) {
        validatePagination(page, size);
        Sort resolvedSort = resolveSort(sort);
        Pageable pageable = PageRequest.of(page, size, resolvedSort);
        Specification<Task> spec = Specification.where(TaskSpecification.notDeleted())
                .and(TaskSpecification.assigneeId(actor.getId()))
                .and(TaskSpecification.projectId(projectId));
        if (!isAdminOrPm(actor)) {
            spec = spec.and(TaskSpecification.memberOf(actor.getId()));
        }
        return PageResponse.of(taskRepository.findAll(spec, pageable), taskMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskSummaryResponse> today(UserPrincipal actor, UUID projectId,
            int page, int size, String sort) {
        validatePagination(page, size);
        Pageable pageable = PageRequest.of(page, size, resolveSort(sort));
        Specification<Task> spec = Specification.where(TaskSpecification.notDeleted())
                .and(TaskSpecification.assigneeId(actor.getId()))
                .and(TaskSpecification.projectId(projectId))
                .and((root, query, cb) -> cb.equal(root.get("dueDate"), LocalDate.now()));
        if (!isAdminOrPm(actor)) {
            spec = spec.and(TaskSpecification.memberOf(actor.getId()));
        }
        return PageResponse.of(taskRepository.findAll(spec, pageable), taskMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskSummaryResponse> overdue(UserPrincipal actor, UUID projectId,
            int page, int size, String sort) {
        validatePagination(page, size);
        Pageable pageable = PageRequest.of(page, size, resolveSort(sort));
        Specification<Task> spec = Specification.where(TaskSpecification.notDeleted())
                .and(TaskSpecification.assigneeId(actor.getId()))
                .and(TaskSpecification.projectId(projectId))
                .and(TaskSpecification.overdue(LocalDate.now()));
        if (!isAdminOrPm(actor)) {
            spec = spec.and(TaskSpecification.memberOf(actor.getId()));
        }
        return PageResponse.of(taskRepository.findAll(spec, pageable), taskMapper::toSummary);
    }

    @Transactional
    public TaskResponse update(UserPrincipal actor, UUID taskId, TaskUpdateRequest request) {
        Task task = findActive(taskId);
        Project project = task.getProject();
        ensureCanView(project, actor);

        if (task.getVersion() != request.version()) {
            throw new ConflictException();
        }

        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setPriority(request.priority() != null ? request.priority() : task.getPriority());
        task.setType(request.type() != null ? request.type() : task.getType());
        task.setSource(request.source() != null ? request.source() : task.getSource());
        task.setStartDate(request.startDate());
        task.setDueDate(request.dueDate());
        task.setEstimateMinutes(request.estimateMinutes());
        task.setEstimateUnit(request.estimateUnit() != null ? request.estimateUnit() : task.getEstimateUnit());
        applyParent(task, request.parentTaskId(), actor);
        validateDates(task);
        task.setAssignee(request.assigneeId() != null
                ? findProjectMemberUser(project, request.assigneeId())
                : null);

        if (request.collaboratorIds() != null) {
            replaceCollaboratorsInternal(task, request.collaboratorIds(), actor);
        }
        if (request.watcherIds() != null) {
            replaceWatchersInternal(task, request.watcherIds(), actor);
        }
        if (request.tagIds() != null) {
            replaceTagsInternal(task, request.tagIds(), actor);
        }
        if (request.sprintId() != null) {
            sprintRepository.findByIdAndDeletedAtIsNull(request.sprintId())
                    .filter(s -> s.getProjectId().equals(project.getId()))
                    .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "Sprint không hợp lệ cho dự án này"));
            task.setSprintId(request.sprintId());
        }

        TaskStatus newStatus = request.status() != null ? request.status() : task.getStatus();
        applyStatusRules(task, newStatus, request.blocked(), request.blockerReason());
        if (request.progress() != null) {
            if (task.getStatus() == TaskStatus.DONE && request.progress() < 100) {
                throw new BusinessException(ErrorCode.PROGRESS_REQUIRED_FOR_DONE);
            }
            task.setProgress(request.progress());
        }
        if (request.notes() != null) {
            task.setNotes(request.notes());
        }

        Task saved = taskRepository.save(task);
        auditService.record("TASK_UPDATED", ENTITY_TYPE, saved.getId(),
                Map.of("code", saved.getCode(), "title", saved.getTitle()));
        log.info("task.update success id={} actor={}", saved.getId(), actor.getUsername());
        return toResponse(saved);
    }

    @Transactional
    public void delete(UserPrincipal actor, UUID taskId) {
        Task task = findActive(taskId);
        ensureCanManage(task.getProject(), actor);

        long children = taskRepository.countByParentTaskIdAndDeletedAtIsNull(taskId);
        if (children > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Công việc còn " + children + " công việc con, không thể xóa (BR-TASK-17)");
        }

        task.setDeletedAt(Instant.now());
        task.setDeletedBy(actor.getId());
        taskRepository.save(task);
        auditService.record("TASK_DELETED", ENTITY_TYPE, taskId,
                Map.of("code", task.getCode()));
        log.info("task.delete success id={} actor={}", taskId, actor.getUsername());
    }

    // --------------------------------------------------------- sub-resources

    @Transactional
    public TaskResponse changeAssignee(UserPrincipal actor, UUID taskId, UUID assigneeId) {
        Task task = findActive(taskId);
        ensureCanView(task.getProject(), actor);
        UUID from = task.getAssignee() != null ? task.getAssignee().getId() : null;
        task.setAssignee(assigneeId != null
                ? findProjectMemberUser(task.getProject(), assigneeId)
                : null);
        Task saved = taskRepository.save(task);
        auditService.record("TASK_ASSIGNEE_CHANGED", ENTITY_TYPE, saved.getId(),
                Map.of("assigneeId", String.valueOf(from)), Map.of("assigneeId", String.valueOf(assigneeId)));
        log.info("task.assignee id={} assigneeId={} actor={}", saved.getId(), assigneeId, actor.getUsername());
        return toResponse(saved);
    }

    @Transactional
    public TaskResponse changeStatus(UserPrincipal actor, UUID taskId, StatusUpdateRequest request) {
        Task task = findActive(taskId);
        TaskStatus from = task.getStatus();
        TaskStatus to = request.status();

        boolean allowed = isAdminOrPm(actor)
                || isProjectManager(task.getProject(), actor.getId())
                || (task.getAssignee() != null && task.getAssignee().getId().equals(actor.getId()));
        if (!allowed) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        if (!isTransitionAllowed(from, to)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Không thể chuyển trạng thái từ " + from + " sang " + to);
        }

        if (to == TaskStatus.BLOCKED) {
            if (request.blockerReason() == null || request.blockerReason().isBlank()) {
                throw new BusinessException(ErrorCode.BLOCKER_REASON_REQUIRED);
            }
            task.setBlocked(true);
            task.setBlockerReason(request.blockerReason());
        } else {
            task.setBlocked(false);
            task.setBlockerReason(null);
        }
        if (to == TaskStatus.DONE) {
            task.setProgress(100);
            task.setActualCompletedAt(Instant.now());
        }
        task.setStatus(to);

        Task saved = taskRepository.save(task);
        auditService.record("TASK_STATUS_CHANGE", ENTITY_TYPE, saved.getId(),
                Map.of("status", from.name()), Map.of("status", to.name()));
        log.info("task.status id={} {} -> {} actor={}", saved.getId(), from, to, actor.getUsername());
        return toResponse(saved);
    }

    @Transactional
    public TaskResponse changeProgress(UserPrincipal actor, UUID taskId, ProgressUpdateRequest request) {
        Task task = findActive(taskId);
        ensureCanUpdateOrAssignee(task, actor);
        if (task.getStatus() == TaskStatus.DONE && request.progress() < 100) {
            throw new BusinessException(ErrorCode.PROGRESS_REQUIRED_FOR_DONE);
        }
        int from = task.getProgress();
        task.setProgress(request.progress());
        Task saved = taskRepository.save(task);
        auditService.record("TASK_PROGRESS_CHANGE", ENTITY_TYPE, saved.getId(),
                Map.of("progress", from), Map.of("progress", saved.getProgress()));
        return toResponse(saved);
    }

    @Transactional
    public TaskResponse changeBlocker(UserPrincipal actor, UUID taskId, BlockerUpdateRequest request) {
        Task task = findActive(taskId);
        ensureCanUpdateOrAssignee(task, actor);
        boolean blocked = Boolean.TRUE.equals(request.blocked());
        if (blocked && (request.blockerReason() == null || request.blockerReason().isBlank())) {
            throw new BusinessException(ErrorCode.BLOCKER_REASON_REQUIRED);
        }
        task.setBlocked(blocked);
        task.setBlockerReason(blocked ? request.blockerReason() : null);
        Task saved = taskRepository.save(task);
        auditService.record("TASK_BLOCKER_CHANGE", ENTITY_TYPE, saved.getId(),
                Map.of("blocked", blocked));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<TaskSummaryResponse> listChildren(UUID taskId, UserPrincipal actor) {
        Task task = findActive(taskId);
        ensureCanView(task.getProject(), actor);
        return taskRepository.findByParentTaskIdAndDeletedAtIsNullOrderByCreatedAtAsc(taskId).stream()
                .map(taskMapper::toSummary)
                .toList();
    }

    @Transactional
    public TaskResponse replaceTags(UserPrincipal actor, UUID taskId, TagIdsRequest request) {
        Task task = findActive(taskId);
        ensureCanManage(task.getProject(), actor);
        List<UUID> tagIds = request.tagIds() == null ? List.of() : request.tagIds();
        for (UUID tagId : tagIds) {
            if (!tagRepository.existsById(tagId)) {
                throw new ResourceNotFoundException("tag", tagId);
            }
        }
        replaceTagsInternal(task, tagIds, actor);
        auditService.record("TASK_TAGS_CHANGE", ENTITY_TYPE, taskId, Map.of("tagIds", tagIds.toString()));
        return toResponse(taskRepository.findById(taskId).orElseThrow());
    }

    @Transactional
    public TaskResponse replaceCollaborators(UserPrincipal actor, UUID taskId, UserIdsRequest request) {
        Task task = findActive(taskId);
        ensureCanManage(task.getProject(), actor);
        List<UUID> userIds = request.userIds() == null ? List.of() : request.userIds();
        replaceCollaboratorsInternal(task, userIds, actor);
        auditService.record("TASK_COLLABORATORS_CHANGE", ENTITY_TYPE, taskId, Map.of("userIds", userIds.toString()));
        return toResponse(taskRepository.findById(taskId).orElseThrow());
    }

    @Transactional
    public TaskResponse replaceWatchers(UserPrincipal actor, UUID taskId, UserIdsRequest request) {
        Task task = findActive(taskId);
        ensureCanManage(task.getProject(), actor);
        List<UUID> userIds = request.userIds() == null ? List.of() : request.userIds();
        replaceWatchersInternal(task, userIds, actor);
        auditService.record("TASK_WATCHERS_CHANGE", ENTITY_TYPE, taskId, Map.of("userIds", userIds.toString()));
        return toResponse(taskRepository.findById(taskId).orElseThrow());
    }

    // ------------------------------------------------------------- comments

    @Transactional(readOnly = true)
    public List<CommentResponse> listComments(UUID taskId, UserPrincipal actor) {
        Task task = findActive(taskId);
        ensureCanView(task.getProject(), actor);
        List<TaskComment> comments = commentRepository.findByTaskIdAndDeletedAtIsNullOrderByCreatedAtAsc(taskId);
        Map<UUID, User> users = loadUsers(comments.stream()
                .map(TaskComment::getCreatedBy)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        return comments.stream()
                .map(c -> taskMapper.toCommentResponse(c, users.get(c.getCreatedBy())))
                .toList();
    }

    @Transactional
    public CommentResponse addComment(UserPrincipal actor, UUID taskId, CommentRequest request) {
        Task task = findActive(taskId);
        ensureCanView(task.getProject(), actor);
        TaskComment comment = new TaskComment();
        comment.setTask(task);
        comment.setContent(request.content());
        comment.setCreatedBy(actor.getId());
        comment.setUpdatedBy(actor.getId());
        TaskComment saved = commentRepository.save(comment);
        log.info("task.comment.added taskId={} actor={}", taskId, actor.getUsername());
        return taskMapper.toCommentResponse(saved, findActiveUser(actor.getId()));
    }

    @Transactional
    public CommentResponse updateComment(UserPrincipal actor, UUID taskId, UUID commentId,
            CommentRequest request) {
        findActive(taskId);
        TaskComment comment = findActiveComment(commentId);
        if (!comment.getCreatedBy().equals(actor.getId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        comment.setContent(request.content());
        comment.setUpdatedBy(actor.getId());
        TaskComment saved = commentRepository.save(comment);
        return taskMapper.toCommentResponse(saved, findActiveUser(actor.getId()));
    }

    @Transactional
    public void deleteComment(UserPrincipal actor, UUID taskId, UUID commentId) {
        findActive(taskId);
        TaskComment comment = findActiveComment(commentId);
        if (!comment.getCreatedBy().equals(actor.getId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        comment.setDeletedAt(Instant.now());
        comment.setDeletedBy(actor.getId());
        commentRepository.save(comment);
        log.info("task.comment.deleted taskId={} commentId={} actor={}", taskId, commentId,
                actor.getUsername());
    }

    // ---------------------------------------------------------- attachments

    @Transactional(readOnly = true)
    public List<AttachmentResponse> listAttachments(UUID taskId, UserPrincipal actor) {
        Task task = findActive(taskId);
        ensureCanView(task.getProject(), actor);
        List<Attachment> attachments = attachmentRepository.findByTaskIdAndDeletedAtIsNullOrderByCreatedAtAsc(taskId);
        Map<UUID, User> users = loadUsers(attachments.stream()
                .map(Attachment::getUploadedBy)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        return attachments.stream()
                .map(a -> taskMapper.toAttachmentResponse(a, users.get(a.getUploadedBy()),
                        downloadUrl(taskId, a.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public DownloadResult downloadAttachment(UUID taskId, UUID attachmentId, UserPrincipal actor) {
        Task task = findActive(taskId);
        ensureCanView(task.getProject(), actor);
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .filter(a -> a.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("file đính kèm", attachmentId));
        if (!taskId.equals(attachment.getTask() != null ? attachment.getTask().getId() : null)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        try {
            byte[] bytes = Files.readAllBytes(Path.of(attachment.getFilePath()));
            return new DownloadResult(attachment.getFileName(), attachment.getContentType(),
                    new org.springframework.core.io.ByteArrayResource(bytes));
        } catch (java.io.IOException ex) {
            throw new ResourceNotFoundException("file đính kèm", attachmentId);
        }
    }

    /**
     * Kết quả tải file đính kèm.
     */
    public record DownloadResult(String fileName, String contentType,
            org.springframework.core.io.ByteArrayResource resource) {
    }

    @Transactional
    public AttachmentResponse uploadAttachment(UserPrincipal actor, UUID taskId, MultipartFile file) {
        Task task = findActive(taskId);
        ensureCanView(task.getProject(), actor);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Chưa chọn file để tải lên");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE);
        }
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        if (!MIME_WHITELIST.contains(contentType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Loại file không được hỗ trợ: " + contentType);
        }

        Attachment attachment = new Attachment();
        attachment.setTask(task);
        attachment.setFileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "file");
        attachment.setContentType(contentType);
        attachment.setSizeBytes(file.getSize());
        attachment.setUploadedBy(actor.getId());
        attachment.setFilePath(storeFile(taskId, file));
        Attachment saved = attachmentRepository.save(attachment);

        auditService.record("TASK_ATTACHMENT_UPLOADED", ENTITY_TYPE, taskId,
                Map.of("attachmentId", saved.getId().toString(), "fileName", saved.getFileName()));
        log.info("task.attachment.uploaded taskId={} attachmentId={} actor={}", taskId, saved.getId(),
                actor.getUsername());
        return taskMapper.toAttachmentResponse(saved, findActiveUser(actor.getId()),
                downloadUrl(taskId, saved.getId()));
    }

    @Transactional
    public void deleteAttachment(UserPrincipal actor, UUID taskId, UUID attachmentId) {
        findActive(taskId);
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .filter(a -> a.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("file đính kèm", attachmentId));
        if (!taskId.equals(attachment.getTask() != null ? attachment.getTask().getId() : null)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        attachment.setDeletedAt(Instant.now());
        attachment.setDeletedBy(actor.getId());
        attachmentRepository.save(attachment);
        deleteFile(attachment.getFilePath());
        auditService.record("TASK_ATTACHMENT_DELETED", ENTITY_TYPE, taskId,
                Map.of("attachmentId", attachmentId.toString()));
        log.info("task.attachment.deleted taskId={} attachmentId={} actor={}", taskId, attachmentId,
                actor.getUsername());
    }

    // -------------------------------------------------------------- history

    @Transactional(readOnly = true)
    public List<TaskHistoryEntry> history(UUID taskId, UserPrincipal actor) {
        Task task = findActive(taskId);
        ensureCanView(task.getProject(), actor);
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtAsc(ENTITY_TYPE, taskId).stream()
                .map(this::toHistoryEntry)
                .toList();
    }

    // --------------------------------------------------------------- export

    @Transactional(readOnly = true)
    public byte[] export(UserPrincipal actor, String keyword, UUID projectId, UUID assigneeId,
            List<TaskStatus> statuses, List<TaskPriority> priorities, List<TaskType> types, UUID tagId,
            LocalDate startDateFrom, LocalDate startDateTo,
            LocalDate dueDateFrom, LocalDate dueDateTo,
            Boolean overdue, Boolean blocked) {
        if (!isAdminOrPm(actor)) {
            ensureCanManage(findActiveProject(projectId), actor);
        }
        Specification<Task> spec = Specification.where(TaskSpecification.notDeleted())
                .and(TaskSpecification.keyword(keyword))
                .and(TaskSpecification.projectId(projectId))
                .and(TaskSpecification.assigneeId(assigneeId))
                .and(TaskSpecification.statuses(statuses))
                .and(TaskSpecification.priorities(priorities))
                .and(TaskSpecification.types(types))
                .and(TaskSpecification.tagId(tagId))
                .and(TaskSpecification.startDateRange(startDateFrom, startDateTo))
                .and(TaskSpecification.dueDateRange(dueDateFrom, dueDateTo))
                .and(TaskSpecification.overdue(overdue != null && overdue ? LocalDate.now() : null))
                .and(blocked == null ? null : TaskSpecification.blocked(blocked));
        if (!isAdminOrPm(actor)) {
            spec = spec.and(TaskSpecification.memberOf(actor.getId()));
        }

        long count = taskRepository.count(spec);
        if (count > MAX_EXPORT_ROWS) {
            throw new BusinessException(ErrorCode.EXPORT_LIMIT_EXCEEDED,
                    "Có " + count + " công việc, vượt giới hạn " + MAX_EXPORT_ROWS + " dòng (BR-TASK-18)");
        }

        List<Task> tasks = taskRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "code"));
        auditService.record("TASK_EXPORT", ENTITY_TYPE, null, Map.of("rows", count));
        log.info("task.export rows={} actor={}", count, actor.getUsername());
        return buildExcel(tasks);
    }

    // ---------------------------------------------------------------- helpers

    private byte[] buildExcel(List<Task> tasks) {
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
                var out = new java.io.ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("tasks");
            String[] headers = {"Code", "Tiêu đề", "Trạng thái", "Ưu tiên", "Người thực hiện",
                    "Ngày bắt đầu", "Hạn", "Tiến độ", "Blocker", "Project"};
            var headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }
            int rowIndex = 1;
            for (Task task : tasks) {
                var row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(task.getCode());
                row.createCell(1).setCellValue(task.getTitle());
                row.createCell(2).setCellValue(task.getStatus().name());
                row.createCell(3).setCellValue(task.getPriority().name());
                row.createCell(4).setCellValue(task.getAssignee() != null
                        ? task.getAssignee().getFullName() : "");
                row.createCell(5).setCellValue(task.getStartDate() != null
                        ? task.getStartDate().toString() : "");
                row.createCell(6).setCellValue(task.getDueDate() != null
                        ? task.getDueDate().toString() : "");
                row.createCell(7).setCellValue(task.getProgress());
                row.createCell(8).setCellValue(task.isBlocked()
                        ? (task.getBlockerReason() != null ? task.getBlockerReason() : "Có") : "");
                row.createCell(9).setCellValue(task.getProject() != null ? task.getProject().getCode() : "");
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (java.io.IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Không thể tạo file Excel");
        }
    }

    private void applyParent(Task task, UUID parentTaskId, UserPrincipal actor) {
        if (parentTaskId == null) {
            task.setParentTask(null);
            return;
        }
        Task parent = findActive(parentTaskId);
        if (!parent.getProject().getId().equals(task.getProject().getId())) {
            throw new BusinessException(ErrorCode.PARENT_TASK_PROJECT_MISMATCH);
        }
        Task cursor = parent;
        while (cursor != null) {
            if (cursor.getId().equals(task.getId())) {
                throw new BusinessException(ErrorCode.CIRCULAR_PARENT);
            }
            cursor = cursor.getParentTask();
        }
        task.setParentTask(parent);
    }

    private void applyStatusRules(Task task, TaskStatus status, Boolean blocked, String blockerReason) {
        boolean wantBlocked = Boolean.TRUE.equals(blocked) || status == TaskStatus.BLOCKED;
        if (status == TaskStatus.DONE) {
            if (task.getProgress() < 100) {
                task.setProgress(100);
            }
            if (task.getActualCompletedAt() == null) {
                task.setActualCompletedAt(Instant.now());
            }
        }
        task.setStatus(status);
        if (wantBlocked) {
            task.setBlocked(true);
            if (blockerReason != null && !blockerReason.isBlank()) {
                task.setBlockerReason(blockerReason);
            } else if (task.getBlockerReason() == null || task.getBlockerReason().isBlank()) {
                task.setBlockerReason("Tắc nghẽn");
            }
        } else {
            task.setBlocked(false);
            task.setBlockerReason(null);
        }
    }

    private boolean isTransitionAllowed(TaskStatus from, TaskStatus to) {
        if (from == to) {
            return true;
        }
        if (to == TaskStatus.CANCELLED) {
            return from != TaskStatus.CANCELLED;
        }
        if (to == TaskStatus.BLOCKED) {
            return from != TaskStatus.DONE && from != TaskStatus.CANCELLED;
        }
        return switch (from) {
            case TODO -> to == TaskStatus.IN_PROGRESS;
            case IN_PROGRESS -> to == TaskStatus.REVIEW;
            case REVIEW -> to == TaskStatus.DONE || to == TaskStatus.IN_PROGRESS;
            case BLOCKED -> to == TaskStatus.IN_PROGRESS;
            case DONE, CANCELLED -> false;
        };
    }

    private void replaceCollaboratorsInternal(Task task, List<UUID> userIds, UserPrincipal actor) {
        assigneeRepository.deleteByTaskId(task.getId());
        for (UUID userId : userIds) {
            User user = findProjectMemberUser(task.getProject(), userId);
            TaskAssignee assignee = new TaskAssignee();
            assignee.setTask(task);
            assignee.setUser(user);
            assignee.setCreatedBy(actor.getId());
            assigneeRepository.save(assignee);
        }
    }

    private void replaceWatchersInternal(Task task, List<UUID> userIds, UserPrincipal actor) {
        watcherRepository.deleteByTaskId(task.getId());
        for (UUID userId : userIds) {
            User user = findProjectMemberUser(task.getProject(), userId);
            TaskWatcher watcher = new TaskWatcher();
            watcher.setTask(task);
            watcher.setUser(user);
            watcher.setCreatedBy(actor.getId());
            watcherRepository.save(watcher);
        }
    }

    private void replaceTagsInternal(Task task, List<UUID> tagIds, UserPrincipal actor) {
        taskTagRepository.deleteByTaskId(task.getId());
        for (UUID tagId : tagIds) {
            Tag tag = tagRepository.findById(tagId)
                    .orElseThrow(() -> new ResourceNotFoundException("tag", tagId));
            TaskTag taskTag = new TaskTag();
            taskTag.setTask(task);
            taskTag.setTag(tag);
            taskTag.setCreatedBy(actor.getId());
            taskTagRepository.save(taskTag);
        }
    }

    private TaskResponse toResponse(Task task) {
        List<TagBriefResponse> tags = taskTagRepository.findByTaskIdOrderByCreatedAtAsc(task.getId()).stream()
                .map(tt -> taskMapper.toTagBrief(tt.getTag()))
                .toList();
        List<UserBriefResponse> collaborators = assigneeRepository.findByTaskIdOrderByCreatedAtAsc(task.getId())
                .stream()
                .map(a -> taskMapper.toUserBrief(a.getUser()))
                .toList();
        List<UserBriefResponse> watchers = watcherRepository.findByTaskIdOrderByCreatedAtAsc(task.getId())
                .stream()
                .map(w -> taskMapper.toUserBrief(w.getUser()))
                .toList();
        TaskResponse response = taskMapper.toResponse(task);
        return new TaskResponse(
                response.id(), response.code(), response.projectId(), response.projectCode(), response.projectName(),
                response.parentTaskId(),
                response.title(), response.description(), response.status(), response.priority(),
                response.type(), response.source(), response.assignee(), response.reporter(),
                response.progress(), response.blocked(), response.blockerReason(),
                response.startDate(), response.dueDate(), response.actualCompletedAt(),
                response.estimateMinutes(), response.estimateUnit(), response.actualMinutes(), response.notes(),
                tags, collaborators, watchers,
                commentRepository.countByTaskIdAndDeletedAtIsNull(task.getId()),
                attachmentRepository.countByTaskIdAndDeletedAtIsNull(task.getId()),
                response.createdAt(), response.updatedAt(), response.sprintId(), response.version());
    }

    private TaskHistoryEntry toHistoryEntry(AuditLog logEntry) {
        Map<String, TaskHistoryEntry.Change> changes = new LinkedHashMap<>();
        Map<String, Object> before = logEntry.getBeforeData() != null ? logEntry.getBeforeData() : Map.of();
        Map<String, Object> after = logEntry.getAfterData() != null ? logEntry.getAfterData() : Map.of();
        Set<String> keys = new java.util.LinkedHashSet<>(before.keySet());
        keys.addAll(after.keySet());
        for (String key : keys) {
            Object from = before.get(key);
            Object to = after.get(key);
            if (!Objects.equals(from, to)) {
                changes.put(key, new TaskHistoryEntry.Change(from, to));
            }
        }
        return new TaskHistoryEntry(logEntry.getCreatedAt(), logEntry.getActorId(),
                logEntry.getActorUsername(), logEntry.getAction(), changes);
    }

    private String storeFile(UUID taskId, MultipartFile file) {
        try {
            Path dir = Path.of(storagePath, "tasks", taskId.toString());
            Files.createDirectories(dir);
            String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
            String storedName = UUID.randomUUID() + "_" + original.replaceAll("[^a-zA-Z0-9._-]", "_");
            Path target = dir.resolve(storedName);
            file.transferTo(target);
            return target.toString();
        } catch (java.io.IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Không thể lưu file");
        }
    }

    private void deleteFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(filePath));
        } catch (java.io.IOException ex) {
            log.warn("task.attachment.deleteFile failed path={} error={}", filePath, ex.getMessage());
        }
    }

    private String downloadUrl(UUID taskId, UUID attachmentId) {
        return "/api/v1/tasks/" + taskId + "/attachments/" + attachmentId + "/download";
    }

    private Map<UUID, User> loadUsers(List<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }

    private Task findActive(UUID taskId) {
        return taskRepository.findById(taskId)
                .filter(t -> t.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("công việc", taskId));
    }

    private TaskComment findActiveComment(UUID commentId) {
        return commentRepository.findById(commentId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("bình luận", commentId));
    }

    private Project findActiveProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("dự án", projectId));
    }

    private User findActiveUser(UUID userId) {
        return userRepository.findById(userId)
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("tài khoản", userId));
    }

    private User findProjectMemberUser(Project project, UUID userId) {
        User user = findActiveUser(userId);
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

    private void ensureCanUpdateOrAssignee(Task task, UserPrincipal actor) {
        if (isAdminOrPm(actor)
                || isProjectManager(task.getProject(), actor.getId())
                || (task.getAssignee() != null && task.getAssignee().getId().equals(actor.getId()))) {
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

    private void validateDates(Task task) {
        if (task.getStartDate() != null && task.getDueDate() != null
                && task.getDueDate().isBefore(task.getStartDate())) {
            throw new BusinessException(ErrorCode.INVALID_DATE_RANGE,
                    "Ngày hạn không được nhỏ hơn ngày bắt đầu");
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
}
