package com.example.pmdaily.meeting;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
import com.example.pmdaily.meeting.dto.MeetingCompleteRequest;
import com.example.pmdaily.meeting.dto.MeetingCreateRequest;
import com.example.pmdaily.meeting.dto.MeetingParticipantsRequest;
import com.example.pmdaily.meeting.dto.MeetingResponse;
import com.example.pmdaily.meeting.dto.MeetingUpdateRequest;
import com.example.pmdaily.security.UserPrincipal;
import com.example.pmdaily.task.dto.AttachmentResponse;

import jakarta.validation.Valid;

/**
 * API cuộc họp (docs/api/06-meeting-api.md) — 11 endpoints, FR-MEET-01..07.
 * Endpoint download file đính kèm là mở rộng v1 (để AttachmentResponse.filePath hoạt động).
 * Quyền toàn cục qua @PreAuthorize; kiểm tra kép membership/PM dự án trong MeetingService.
 */
@RestController
@RequestMapping("/api/v1/meetings")
public class MeetingController {

    private final MeetingService meetingService;

    public MeetingController(MeetingService meetingService) {
        this.meetingService = meetingService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('meeting:view')")
    public PageResponse<MeetingResponse> list(
            @AuthenticationPrincipal UserPrincipal actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) MeetingStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toTime) {
        return meetingService.search(actor, keyword, projectId, status, fromTime, toTime, page, size, sort);
    }

    @GetMapping("/today")
    @PreAuthorize("hasAuthority('meeting:view')")
    public List<MeetingResponse> today(@AuthenticationPrincipal UserPrincipal actor) {
        return meetingService.today(actor);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('meeting:manage')")
    public MeetingResponse create(@AuthenticationPrincipal UserPrincipal actor,
            @Valid @RequestBody MeetingCreateRequest request) {
        return meetingService.create(actor, request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('meeting:view')")
    public MeetingResponse get(@AuthenticationPrincipal UserPrincipal actor, @PathVariable UUID id) {
        return meetingService.get(id, actor);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('meeting:manage')")
    public MeetingResponse update(@AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id,
            @Valid @RequestBody MeetingUpdateRequest request) {
        return meetingService.update(actor, id, request);
    }

    @PutMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('meeting:view')")
    public MeetingResponse complete(@AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id,
            @Valid @RequestBody MeetingCompleteRequest request) {
        return meetingService.complete(actor, id, request);
    }

    @PutMapping("/{id}/participants")
    @PreAuthorize("hasAuthority('meeting:manage')")
    public MeetingResponse updateParticipants(@AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id,
            @Valid @RequestBody MeetingParticipantsRequest request) {
        return meetingService.updateParticipants(actor, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('meeting:manage')")
    public void delete(@AuthenticationPrincipal UserPrincipal actor, @PathVariable UUID id) {
        meetingService.delete(actor, id);
    }

    @GetMapping("/{id}/attachments")
    @PreAuthorize("hasAuthority('meeting:view')")
    public List<AttachmentResponse> attachments(@AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id) {
        return meetingService.listAttachments(id, actor);
    }

    @PostMapping("/{id}/attachments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('meeting:manage')")
    public AttachmentResponse uploadAttachment(@AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file) {
        return meetingService.uploadAttachment(actor, id, file);
    }

    @GetMapping("/{id}/attachments/{attachmentId}/download")
    @PreAuthorize("hasAuthority('meeting:view')")
    public ResponseEntity<Resource> downloadAttachment(@AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id,
            @PathVariable UUID attachmentId) {
        var result = meetingService.downloadAttachment(id, attachmentId, actor);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(result.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(result.fileName()).build().toString())
                .body(result.resource());
    }

    @DeleteMapping("/{id}/attachments/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('meeting:manage')")
    public void deleteAttachment(@AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id,
            @PathVariable UUID attachmentId) {
        meetingService.deleteAttachment(actor, id, attachmentId);
    }
}
