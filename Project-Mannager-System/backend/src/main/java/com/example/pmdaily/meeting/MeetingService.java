package com.example.pmdaily.meeting;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.pmdaily.actionitem.ActionItem;
import com.example.pmdaily.actionitem.ActionItemRepository;
import com.example.pmdaily.actionitem.dto.ActionItemSummary;
import com.example.pmdaily.actionitem.mapper.ActionItemMapper;
import com.example.pmdaily.audit.AuditService;
import com.example.pmdaily.common.ErrorCode;
import com.example.pmdaily.common.PageResponse;
import com.example.pmdaily.exception.BusinessException;
import com.example.pmdaily.exception.ConflictException;
import com.example.pmdaily.exception.ResourceNotFoundException;
import com.example.pmdaily.meeting.dto.MeetingCompleteRequest;
import com.example.pmdaily.meeting.dto.MeetingCreateRequest;
import com.example.pmdaily.meeting.dto.MeetingParticipantsRequest;
import com.example.pmdaily.meeting.dto.MeetingResponse;
import com.example.pmdaily.meeting.dto.MeetingUpdateRequest;
import com.example.pmdaily.meeting.mapper.MeetingMapper;
import com.example.pmdaily.project.Project;
import com.example.pmdaily.project.ProjectMemberRepository;
import com.example.pmdaily.project.ProjectMemberRole;
import com.example.pmdaily.project.ProjectRepository;
import com.example.pmdaily.security.UserPrincipal;
import com.example.pmdaily.task.Attachment;
import com.example.pmdaily.task.AttachmentRepository;
import com.example.pmdaily.task.dto.AttachmentResponse;
import com.example.pmdaily.task.dto.UserBriefResponse;
import com.example.pmdaily.user.User;
import com.example.pmdaily.user.UserRepository;
import com.example.pmdaily.user.UserStatus;

/**
 * Nghiệp vụ cuộc họp (docs/api/06-meeting-api.md, UC-006, BR-MEET-01..06).
 * Kiểm tra kép: quyền toàn cục (controller @PreAuthorize) + membership/PM dự án tại service.
 * Notification MEETING_INVITED (hậu điều kiện tạo họp) được ghi nhận khi module Notification
 * triển khai — xem docs/build/environment-check.md muc 9.
 */
@Service
public class MeetingService {

    private static final Logger log = LoggerFactory.getLogger(MeetingService.class);
    private static final String ENTITY_TYPE = "MEETING";
    private static final List<String> SORT_WHITELIST =
            List.of("title", "startTime", "endTime", "status", "createdAt");
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> MIME_WHITELIST = Set.of(
            "image/png", "image/jpg", "image/jpeg", "image/gif",
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain");

    private final MeetingRepository meetingRepository;
    private final MeetingParticipantRepository participantRepository;
    private final AttachmentRepository attachmentRepository;
    private final ActionItemRepository actionItemRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final MeetingMapper mapper;
    private final ActionItemMapper actionItemMapper;
    private final AuditService auditService;

    @Value("${app.storage.path:./uploads}")
    private String storagePath;

    public MeetingService(MeetingRepository meetingRepository,
            MeetingParticipantRepository participantRepository,
            AttachmentRepository attachmentRepository,
            ActionItemRepository actionItemRepository,
            ProjectRepository projectRepository,
            ProjectMemberRepository memberRepository,
            UserRepository userRepository,
            MeetingMapper mapper,
            ActionItemMapper actionItemMapper,
            AuditService auditService) {
        this.meetingRepository = meetingRepository;
        this.participantRepository = participantRepository;
        this.attachmentRepository = attachmentRepository;
        this.actionItemRepository = actionItemRepository;
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.mapper = mapper;
        this.actionItemMapper = actionItemMapper;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------ CRUD

    @Transactional
    public MeetingResponse create(UserPrincipal actor, MeetingCreateRequest request) {
        Project project = findActiveProject(request.projectId());
        ensureCanManage(project, actor);
        validateTimes(request.startTime(), request.endTime());
        validatePlace(request.location(), request.meetingLink());

        Meeting meeting = new Meeting();
        meeting.setProject(project);
        meeting.setTitle(request.title().trim());
        meeting.setStartTime(request.startTime());
        meeting.setEndTime(request.endTime());
        meeting.setLocation(trimToNull(request.location()));
        meeting.setMeetingLink(trimToNull(request.meetingLink()));
        meeting.setChairperson(findProjectMemberUser(project, request.chairpersonId()));
        meeting.setAgenda(request.agenda());
        meeting.setStatus(request.status() != null ? request.status() : MeetingStatus.SCHEDULED);
        Meeting saved = meetingRepository.save(meeting);

        List<UUID> participantIds = request.participantIds() != null ? request.participantIds() : List.of();
        replaceParticipantsInternal(saved, participantIds, actor);

        auditService.record("MEETING_CREATED", ENTITY_TYPE, saved.getId(),
                Map.of("title", saved.getTitle(), "projectId", project.getId().toString()));
        log.info("meeting.create success id={} title={} actor={}", saved.getId(), saved.getTitle(),
                actor.getUsername());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public MeetingResponse get(UUID meetingId, UserPrincipal actor) {
        Meeting meeting = findActive(meetingId);
        ensureCanView(meeting.getProject(), actor);
        return toResponse(meeting);
    }

    @Transactional(readOnly = true)
    public PageResponse<MeetingResponse> search(UserPrincipal actor,
            String keyword, UUID projectId, MeetingStatus status,
            Instant fromTime, Instant toTime,
            int page, int size, String sort) {
        validatePagination(page, size);
        if (fromTime != null && toTime != null && fromTime.isAfter(toTime)) {
            throw new BusinessException(ErrorCode.INVALID_DATE_RANGE,
                    "fromTime không được lớn hơn toTime");
        }
        Pageable pageable = PageRequest.of(page, size, resolveSort(sort));
        Specification<Meeting> spec = Specification.where(MeetingSpecification.notDeleted())
                .and(MeetingSpecification.keyword(keyword))
                .and(MeetingSpecification.projectId(projectId))
                .and(MeetingSpecification.statuses(status))
                .and(MeetingSpecification.timeRange(fromTime, toTime));
        if (!isAdminOrPm(actor)) {
            if (projectId != null && !isMember(projectId, actor.getId())) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }
            spec = spec.and(MeetingSpecification.memberOf(actor.getId()));
        }
        Page<Meeting> result = meetingRepository.findAll(spec, pageable);
        return PageResponse.of(result, this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<MeetingResponse> today(UserPrincipal actor) {
        Instant start = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = start.plus(1, ChronoUnit.DAYS);
        Specification<Meeting> spec = Specification.where(MeetingSpecification.notDeleted())
                .and(MeetingSpecification.timeRange(start, end))
                .and((root, query, cb) -> cb.notEqual(root.get("status"), MeetingStatus.CANCELLED));
        if (!isAdminOrPm(actor)) {
            spec = spec.and(MeetingSpecification.memberOf(actor.getId()));
        }
        List<Meeting> meetings = meetingRepository.findAll(spec,
                Sort.by(Sort.Direction.ASC, "startTime"));
        log.info("meeting.today count={} actor={}", meetings.size(), actor.getUsername());
        return meetings.stream().map(this::toResponse).toList();
    }

    @Transactional
    public MeetingResponse update(UserPrincipal actor, UUID meetingId, MeetingUpdateRequest request) {
        Meeting meeting = findActive(meetingId);
        ensureCanManage(meeting.getProject(), actor);
        if (meeting.getVersion() != request.version()) {
            throw new ConflictException();
        }
        if (!meeting.getProject().getId().equals(request.projectId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Không thể đổi dự án của cuộc họp");
        }
        if (meeting.getStatus() == MeetingStatus.CANCELLED && request.status() != null
                && request.status() != MeetingStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Họp đã hủy không thể chuyển sang trạng thái khác (BR-MEET-06)");
        }
        MeetingStatus from = meeting.getStatus();
        validateTimes(request.startTime(), request.endTime());
        validatePlace(request.location(), request.meetingLink());

        meeting.setTitle(request.title().trim());
        meeting.setStartTime(request.startTime());
        meeting.setEndTime(request.endTime());
        meeting.setLocation(trimToNull(request.location()));
        meeting.setMeetingLink(trimToNull(request.meetingLink()));
        meeting.setChairperson(findProjectMemberUser(meeting.getProject(), request.chairpersonId()));
        meeting.setAgenda(request.agenda());
        if (request.status() != null) {
            meeting.setStatus(request.status());
        }
        Meeting saved = meetingRepository.save(meeting);

        if (request.participantIds() != null) {
            replaceParticipantsInternal(saved, request.participantIds(), actor);
        }

        auditService.record("MEETING_UPDATED", ENTITY_TYPE, saved.getId(),
                Map.of("title", saved.getTitle(), "status", from.name()),
                Map.of("title", saved.getTitle(), "status", saved.getStatus().name()));
        log.info("meeting.update success id={} actor={}", saved.getId(), actor.getUsername());
        return toResponse(saved);
    }

    @Transactional
    public MeetingResponse complete(UserPrincipal actor, UUID meetingId, MeetingCompleteRequest request) {
        Meeting meeting = findActive(meetingId);
        boolean chair = meeting.getChairperson() != null
                && meeting.getChairperson().getId().equals(actor.getId());
        boolean manager = isAdminOrPm(actor) || isProjectManager(meeting.getProject(), actor.getId());
        if (!chair && !manager) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        if (meeting.getStatus() == MeetingStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Họp đã hủy không thể hoàn thành (BR-MEET-06)");
        }
        MeetingStatus from = meeting.getStatus();
        meeting.setContent(request.content());
        meeting.setConclusion(request.conclusion());
        meeting.setStatus(MeetingStatus.COMPLETED);
        Meeting saved = meetingRepository.save(meeting);
        auditService.record("MEETING_COMPLETED", ENTITY_TYPE, saved.getId(),
                Map.of("status", from.name()), Map.of("status", MeetingStatus.COMPLETED.name()));
        log.info("meeting.complete success id={} actor={}", saved.getId(), actor.getUsername());
        return toResponse(saved);
    }

    @Transactional
    public MeetingResponse updateParticipants(UserPrincipal actor, UUID meetingId,
            MeetingParticipantsRequest request) {
        Meeting meeting = findActive(meetingId);
        ensureCanManage(meeting.getProject(), actor);
        List<UUID> add = request.add() != null ? request.add() : List.of();
        List<UUID> remove = request.remove() != null ? request.remove() : List.of();
        if (add.stream().distinct().count() != add.size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Danh sách người tham gia không được trùng (BR-MEET-03)");
        }
        for (UUID userId : add) {
            if (participantRepository.existsByMeetingIdAndUser_Id(meetingId, userId)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "Người dùng đã tham gia cuộc họp (BR-MEET-03)");
            }
            User user = findProjectMemberUser(meeting.getProject(), userId);
            MeetingParticipant participant = new MeetingParticipant();
            participant.setMeeting(meeting);
            participant.setUser(user);
            participantRepository.save(participant);
        }
        for (UUID userId : remove) {
            participantRepository.deleteByMeetingIdAndUser_Id(meetingId, userId);
        }
        auditService.record("MEETING_PARTICIPANTS_CHANGE", ENTITY_TYPE, meetingId,
                Map.of("add", add.toString(), "remove", remove.toString()));
        log.info("meeting.participants id={} add={} remove={} actor={}", meetingId, add.size(),
                remove.size(), actor.getUsername());
        return toResponse(meetingRepository.findById(meetingId).orElseThrow());
    }

    @Transactional
    public void delete(UserPrincipal actor, UUID meetingId) {
        Meeting meeting = findActive(meetingId);
        ensureCanManage(meeting.getProject(), actor);
        meeting.setDeletedAt(Instant.now());
        meeting.setDeletedBy(actor.getId());
        meetingRepository.save(meeting);
        auditService.record("MEETING_DELETED", ENTITY_TYPE, meetingId,
                Map.of("title", meeting.getTitle()));
        log.info("meeting.delete success id={} actor={}", meetingId, actor.getUsername());
    }

    // ---------------------------------------------------------- attachments

    @Transactional(readOnly = true)
    public List<AttachmentResponse> listAttachments(UUID meetingId, UserPrincipal actor) {
        Meeting meeting = findActive(meetingId);
        ensureCanView(meeting.getProject(), actor);
        List<Attachment> attachments = attachmentRepository
                .findByMeetingIdAndDeletedAtIsNullOrderByCreatedAtAsc(meetingId);
        Map<UUID, User> users = loadUsers(attachments.stream()
                .map(Attachment::getUploadedBy)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        return attachments.stream()
                .map(a -> mapper.toAttachmentResponse(a, users.get(a.getUploadedBy()),
                        downloadUrl(meetingId, a.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public DownloadResult downloadAttachment(UUID meetingId, UUID attachmentId, UserPrincipal actor) {
        Meeting meeting = findActive(meetingId);
        ensureCanView(meeting.getProject(), actor);
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .filter(a -> a.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("file đính kèm", attachmentId));
        if (!meetingId.equals(attachment.getMeeting() != null ? attachment.getMeeting().getId() : null)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        try {
            byte[] bytes = Files.readAllBytes(Path.of(attachment.getFilePath()));
            return new DownloadResult(attachment.getFileName(), attachment.getContentType(),
                    new ByteArrayResource(bytes));
        } catch (java.io.IOException ex) {
            throw new ResourceNotFoundException("file đính kèm", attachmentId);
        }
    }

    @Transactional
    public AttachmentResponse uploadAttachment(UserPrincipal actor, UUID meetingId, MultipartFile file) {
        Meeting meeting = findActive(meetingId);
        ensureCanManage(meeting.getProject(), actor);
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
        attachment.setMeeting(meeting);
        attachment.setFileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "file");
        attachment.setContentType(contentType);
        attachment.setSizeBytes(file.getSize());
        attachment.setUploadedBy(actor.getId());
        attachment.setFilePath(storeFile(meetingId, file));
        Attachment saved = attachmentRepository.save(attachment);

        auditService.record("MEETING_ATTACHMENT_UPLOADED", ENTITY_TYPE, meetingId,
                Map.of("attachmentId", saved.getId().toString(), "fileName", saved.getFileName()));
        log.info("meeting.attachment.uploaded meetingId={} attachmentId={} actor={}", meetingId,
                saved.getId(), actor.getUsername());
        return mapper.toAttachmentResponse(saved, findActiveUser(actor.getId()),
                downloadUrl(meetingId, saved.getId()));
    }

    @Transactional
    public void deleteAttachment(UserPrincipal actor, UUID meetingId, UUID attachmentId) {
        findActive(meetingId);
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .filter(a -> a.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("file đính kèm", attachmentId));
        if (!meetingId.equals(attachment.getMeeting() != null ? attachment.getMeeting().getId() : null)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        attachment.setDeletedAt(Instant.now());
        attachment.setDeletedBy(actor.getId());
        attachmentRepository.save(attachment);
        deleteFile(attachment.getFilePath());
        auditService.record("MEETING_ATTACHMENT_DELETED", ENTITY_TYPE, meetingId,
                Map.of("attachmentId", attachmentId.toString()));
        log.info("meeting.attachment.deleted meetingId={} attachmentId={} actor={}", meetingId,
                attachmentId, actor.getUsername());
    }

    /**
     * Kết quả tải file đính kèm.
     */
    public record DownloadResult(String fileName, String contentType, ByteArrayResource resource) {
    }

    // ---------------------------------------------------------------- helpers

    private void replaceParticipantsInternal(Meeting meeting, List<UUID> userIds, UserPrincipal actor) {
        if (userIds.stream().distinct().count() != userIds.size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Danh sách người tham gia không được trùng (BR-MEET-03)");
        }
        participantRepository.deleteByMeetingId(meeting.getId());
        for (UUID userId : userIds) {
            User user = findProjectMemberUser(meeting.getProject(), userId);
            MeetingParticipant participant = new MeetingParticipant();
            participant.setMeeting(meeting);
            participant.setUser(user);
            participantRepository.save(participant);
        }
    }

    private void validateTimes(Instant startTime, Instant endTime) {
        if (endTime.isBefore(startTime) || endTime.equals(startTime)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Thời gian kết thúc phải lớn hơn thời gian bắt đầu (BR-MEET-01)");
        }
    }

    private void validatePlace(String location, String meetingLink) {
        if (trimToNull(location) == null && trimToNull(meetingLink) == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Phải nhập ít nhất địa điểm hoặc link họp (BR-MEET-04)");
        }
    }

    private MeetingResponse toResponse(Meeting meeting) {
        List<MeetingParticipant> participants = participantRepository
                .findByMeetingIdOrderByCreatedAtAsc(meeting.getId());
        List<Attachment> attachments = attachmentRepository
                .findByMeetingIdAndDeletedAtIsNullOrderByCreatedAtAsc(meeting.getId());
        List<ActionItem> actionItems = actionItemRepository
                .findByMeetingIdAndDeletedAtIsNullOrderByCreatedAtAsc(meeting.getId());
        List<UUID> userIds = new ArrayList<>(participants.stream()
                .map(p -> p.getUser().getId())
                .toList());
        if (meeting.getChairperson() != null) {
            userIds.add(meeting.getChairperson().getId());
        }
        attachments.stream().map(Attachment::getUploadedBy).filter(Objects::nonNull).forEach(userIds::add);
        actionItems.stream().map(a -> a.getAssignee().getId()).forEach(userIds::add);
        Map<UUID, User> users = loadUsers(userIds);

        UserBriefResponse chairperson = meeting.getChairperson() != null
                ? mapper.toUserBrief(users.get(meeting.getChairperson().getId()))
                : null;
        List<UserBriefResponse> participantBriefs = participants.stream()
                .map(p -> mapper.toUserBrief(users.get(p.getUser().getId())))
                .toList();
        List<AttachmentResponse> attachmentResponses = attachments.stream()
                .map(a -> mapper.toAttachmentResponse(a, users.get(a.getUploadedBy()),
                        downloadUrl(meeting.getId(), a.getId())))
                .toList();
        List<ActionItemSummary> actionItemSummaries = actionItems.stream()
                .map(a -> actionItemMapper.toSummary(a, mapper.toUserBrief(users.get(a.getAssignee().getId()))))
                .toList();
        return mapper.toResponse(meeting, chairperson, participantBriefs, attachmentResponses,
                actionItemSummaries);
    }

    private String storeFile(UUID meetingId, MultipartFile file) {
        try {
            Path dir = Path.of(storagePath, "meetings", meetingId.toString());
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
            log.warn("meeting.attachment.deleteFile failed path={} error={}", filePath, ex.getMessage());
        }
    }

    private String downloadUrl(UUID meetingId, UUID attachmentId) {
        return "/api/v1/meetings/" + meetingId + "/attachments/" + attachmentId + "/download";
    }

    private Map<UUID, User> loadUsers(List<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }

    private Meeting findActive(UUID meetingId) {
        return meetingRepository.findById(meetingId)
                .filter(m -> m.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("cuộc họp", meetingId));
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

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void validatePagination(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "page phải >= 0 và size phải trong khoảng 1–100");
        }
    }

    private Sort resolveSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "startTime");
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
