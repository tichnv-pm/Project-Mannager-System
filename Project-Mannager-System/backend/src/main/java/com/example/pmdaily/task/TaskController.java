package com.example.pmdaily.task;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.pmdaily.common.PageResponse;
import com.example.pmdaily.security.UserPrincipal;
import com.example.pmdaily.task.dto.AssigneeUpdateRequest;
import com.example.pmdaily.task.dto.AttachmentResponse;
import com.example.pmdaily.task.dto.BlockerUpdateRequest;
import com.example.pmdaily.task.dto.CommentRequest;
import com.example.pmdaily.task.dto.CommentResponse;
import com.example.pmdaily.task.dto.ProgressUpdateRequest;
import com.example.pmdaily.task.dto.StatusUpdateRequest;
import com.example.pmdaily.task.dto.TagIdsRequest;
import com.example.pmdaily.task.dto.TaskCreateRequest;
import com.example.pmdaily.task.dto.TaskHistoryEntry;
import com.example.pmdaily.task.dto.TaskResponse;
import com.example.pmdaily.task.dto.TaskSummaryResponse;
import com.example.pmdaily.task.dto.TaskUpdateRequest;
import com.example.pmdaily.task.dto.UserIdsRequest;

import jakarta.validation.Valid;

/**
 * API công việc (docs/api/05-task-api.md) — 24 endpoints, FR-TASK-01..17.
 * Quyền toàn cục qua @PreAuthorize; kiểm tra kép membership/PM dự án trong TaskService.
 */
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('task:view')")
    public PageResponse<TaskSummaryResponse> list(
            @AuthenticationPrincipal UserPrincipal actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(required = false) List<TaskStatus> status,
            @RequestParam(required = false) List<TaskPriority> priority,
            @RequestParam(required = false) List<TaskType> type,
            @RequestParam(required = false) UUID tagId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDateTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateTo,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(required = false) Boolean blocked,
            @RequestParam(required = false) String sprintId) {
        return taskService.search(actor, keyword, projectId, assigneeId, status, priority, type, tagId,
                startDateFrom, startDateTo, dueDateFrom, dueDateTo, overdue, blocked, sprintId,
                page, size, sort);
    }

    @GetMapping("/my-tasks")
    @PreAuthorize("hasAuthority('task:view')")
    public PageResponse<TaskSummaryResponse> myTasks(@AuthenticationPrincipal UserPrincipal actor,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort) {
        return taskService.myTasks(actor, projectId, page, size, sort);
    }

    @GetMapping("/today")
    @PreAuthorize("hasAuthority('task:view')")
    public PageResponse<TaskSummaryResponse> today(@AuthenticationPrincipal UserPrincipal actor,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort) {
        return taskService.today(actor, projectId, page, size, sort);
    }

    @GetMapping("/overdue")
    @PreAuthorize("hasAuthority('task:view')")
    public PageResponse<TaskSummaryResponse> overdue(@AuthenticationPrincipal UserPrincipal actor,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort) {
        return taskService.overdue(actor, projectId, page, size, sort);
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('task:export')")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal UserPrincipal actor,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(required = false) List<TaskStatus> status,
            @RequestParam(required = false) List<TaskPriority> priority,
            @RequestParam(required = false) List<TaskType> type,
            @RequestParam(required = false) UUID tagId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDateTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateTo,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(required = false) Boolean blocked) {
        byte[] bytes = taskService.export(actor, keyword, projectId, assigneeId, status, priority, type,
                tagId, startDateFrom, startDateTo, dueDateFrom, dueDateTo, overdue, blocked);
        String filename = "tasks-" + new java.time.format.DateTimeFormatterBuilder()
                .appendPattern("yyyyMMdd-HHmmss")
                .toFormatter()
                .format(java.time.LocalDateTime.now());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename + ".xlsx").build().toString())
                .body(bytes);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('task:create')")
    public TaskResponse create(@AuthenticationPrincipal UserPrincipal actor,
            @Valid @RequestBody TaskCreateRequest request) {
        return taskService.create(actor, request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('task:view')")
    public TaskResponse get(@AuthenticationPrincipal UserPrincipal actor, @PathVariable UUID id) {
        return taskService.get(id, actor);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('task:update')")
    public TaskResponse update(@AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id,
            @Valid @RequestBody TaskUpdateRequest request) {
        return taskService.update(actor, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('task:delete')")
    public void delete(@AuthenticationPrincipal UserPrincipal actor, @PathVariable UUID id) {
        taskService.delete(actor, id);
    }

    @PutMapping("/{id}/assignee")
    @PreAuthorize("hasAuthority('task:assign')")
    public TaskResponse changeAssignee(@AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id,
            @Valid @RequestBody AssigneeUpdateRequest request) {
        return taskService.changeAssignee(actor, id, request.assigneeId());
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('task:update')")
    public TaskResponse changeStatus(@AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id,
            @Valid @RequestBody StatusUpdateRequest request) {
        return taskService.changeStatus(actor, id, request);
    }

    @PutMapping("/{id}/progress")
    @PreAuthorize("hasAuthority('task:update')")
    public TaskResponse changeProgress(@AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id,
            @Valid @RequestBody ProgressUpdateRequest request) {
        return taskService.changeProgress(actor, id, request);
    }

    @PutMapping("/{id}/blocker")
    @PreAuthorize("hasAuthority('task:update')")
    public TaskResponse changeBlocker(@AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id,
            @Valid @RequestBody BlockerUpdateRequest request) {
        return taskService.changeBlocker(actor, id, request);
    }

    @GetMapping("/{id}/children")
    @PreAuthorize("hasAuthority('task:view')")
    public List<TaskSummaryResponse> children(@AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id) {
        return taskService.listChildren(id, actor);
    }

    @PutMapping("/{id}/tags")
    @PreAuthorize("hasAuthority('task:update')")
    public TaskResponse replaceTags(@AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id,
            @Valid @RequestBody TagIdsRequest request) {
        return taskService.replaceTags(actor, id, request);
    }

    @PutMapping("/{id}/collaborators")
    @PreAuthorize("hasAuthority('task:assign')")
    public TaskResponse replaceCollaborators(@AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id,
            @Valid @RequestBody UserIdsRequest request) {
        return taskService.replaceCollaborators(actor, id, request);
    }

    @PutMapping("/{id}/watchers")
    @PreAuthorize("hasAuthority('task:update')")
    public TaskResponse replaceWatchers(@AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id,
            @Valid @RequestBody UserIdsRequest request) {
        return taskService.replaceWatchers(actor, id, request);
    }

    @GetMapping("/{id}/comments")
    @PreAuthorize("hasAuthority('task:view')")
    public List<CommentResponse> comments(@AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id) {
        return taskService.listComments(id, actor);
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('task:comment')")
    public CommentResponse addComment(@AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id,
            @Valid @RequestBody CommentRequest request) {
        return taskService.addComment(actor, id, request);
    }

    @PutMapping("/{id}/comments/{commentId}")
    @PreAuthorize("hasAuthority('task:comment')")
    public CommentResponse updateComment(@AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id,
            @PathVariable UUID commentId,
            @Valid @RequestBody CommentRequest request) {
        return taskService.updateComment(actor, id, commentId, request);
    }

    @DeleteMapping("/{id}/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('task:comment')")
    public void deleteComment(@AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id,
            @PathVariable UUID commentId) {
        taskService.deleteComment(actor, id, commentId);
    }

    @GetMapping("/{id}/attachments")
    @PreAuthorize("hasAuthority('task:view')")
    public List<AttachmentResponse> attachments(@AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id) {
        return taskService.listAttachments(id, actor);
    }

    @PostMapping("/{id}/attachments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('task:attachment')")
    public AttachmentResponse uploadAttachment(@AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file) {
        return taskService.uploadAttachment(actor, id, file);
    }

    @DeleteMapping("/{id}/attachments/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('task:attachment')")
    public void deleteAttachment(@AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id,
            @PathVariable UUID attachmentId) {
        taskService.deleteAttachment(actor, id, attachmentId);
    }

    @GetMapping("/{id}/attachments/{attachmentId}/download")
    @PreAuthorize("hasAuthority('task:view')")
    public ResponseEntity<Resource> downloadAttachment(@AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id,
            @PathVariable UUID attachmentId) {
        var result = taskService.downloadAttachment(id, attachmentId, actor);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(result.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(result.fileName()).build().toString())
                .body(result.resource());
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAuthority('task:view')")
    public List<TaskHistoryEntry> history(@AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id) {
        return taskService.history(id, actor);
    }
}
