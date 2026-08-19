package com.example.pmdaily.actionitem;

import java.util.UUID;

import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.pmdaily.actionitem.dto.ActionItemCreateRequest;
import com.example.pmdaily.actionitem.dto.ActionItemResponse;
import com.example.pmdaily.actionitem.dto.ActionItemUpdateRequest;
import com.example.pmdaily.actionitem.dto.ConvertToTaskRequest;
import com.example.pmdaily.common.PageResponse;
import com.example.pmdaily.security.UserPrincipal;
import com.example.pmdaily.task.dto.TaskResponse;

import jakarta.validation.Valid;

/**
 * API action item (docs/api/07-action-item-api.md) — 7 endpoints, FR-AI-01..04.
 * PUT /{id} chỉ yêu cầu action-item:view ở controller — assignee (không có quyền manage)
 * được phép cập nhật status/progress; kiểm tra kép trong ActionItemService.
 */
@RestController
@RequestMapping("/api/v1/action-items")
public class ActionItemController {

    private final ActionItemService actionItemService;

    public ActionItemController(ActionItemService actionItemService) {
        this.actionItemService = actionItemService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('action-item:view')")
    public PageResponse<ActionItemResponse> list(
            @AuthenticationPrincipal UserPrincipal actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID meetingId,
            @RequestParam(required = false) ActionItemStatus status,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(required = false) Boolean overdue) {
        return actionItemService.search(actor, keyword, projectId, meetingId, status, assigneeId,
                overdue, page, size, sort);
    }

    @GetMapping("/overdue")
    @PreAuthorize("hasAuthority('action-item:view')")
    public PageResponse<ActionItemResponse> overdue(@AuthenticationPrincipal UserPrincipal actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort) {
        return actionItemService.overdue(actor, page, size, sort);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('action-item:manage')")
    public ActionItemResponse create(@AuthenticationPrincipal UserPrincipal actor,
            @Valid @RequestBody ActionItemCreateRequest request) {
        return actionItemService.create(actor, request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('action-item:view')")
    public ActionItemResponse get(@AuthenticationPrincipal UserPrincipal actor, @PathVariable UUID id) {
        return actionItemService.get(id, actor);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('action-item:view')")
    public ActionItemResponse update(@AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id,
            @Valid @RequestBody ActionItemUpdateRequest request) {
        return actionItemService.update(actor, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('action-item:manage')")
    public void delete(@AuthenticationPrincipal UserPrincipal actor, @PathVariable UUID id) {
        actionItemService.delete(actor, id);
    }

    @PostMapping("/{id}/convert-to-task")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('action-item:manage')")
    public TaskResponse convertToTask(@AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id,
            @RequestBody ConvertToTaskRequest request) {
        return actionItemService.convertToTask(actor, id, request);
    }
}
