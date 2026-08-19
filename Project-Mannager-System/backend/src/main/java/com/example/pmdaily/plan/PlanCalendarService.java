package com.example.pmdaily.plan;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.pmdaily.audit.AuditService;
import com.example.pmdaily.common.ErrorCode;
import com.example.pmdaily.exception.BusinessException;
import com.example.pmdaily.exception.ConflictException;
import com.example.pmdaily.exception.ResourceNotFoundException;
import com.example.pmdaily.plan.dto.PlanCalendarCreateRequest;
import com.example.pmdaily.plan.dto.PlanCalendarExceptionRequest;
import com.example.pmdaily.plan.dto.PlanCalendarExceptionResponse;
import com.example.pmdaily.plan.dto.PlanCalendarResponse;
import com.example.pmdaily.plan.dto.PlanCalendarUpdateRequest;
import com.example.pmdaily.plan.dto.WorkingDayRequest;
import com.example.pmdaily.plan.dto.WorkingDayResponse;
import com.example.pmdaily.plan.mapper.PlanCalendarMapper;
import com.example.pmdaily.project.ProjectMemberRepository;
import com.example.pmdaily.security.UserPrincipal;

/**
 * Nghiệp vụ Working Calendar (docs/api/13-planning-api.md muc 2.4, docs/planning/08 muc 4) — PLN-FR-CAL-01..05.
 * Rules: PLN-RULE-GEN-*; fallback: parent_calendar_id (project → org), default system/test khi tạo plan
 * (PLN-AC-CAL-03). Exception WORKING biến weekend thành ngày làm việc (PLN-AC-CAL-05).
 */
@Service
@Transactional(readOnly = true)
public class PlanCalendarService {

    private static final Logger log = LoggerFactory.getLogger(PlanCalendarService.class);

    private static final LocalTime DEFAULT_START = LocalTime.of(8, 0);
    private static final LocalTime DEFAULT_END = LocalTime.of(17, 0);

    private final PlanCalendarRepository calendarRepository;
    private final PlanCalendarWorkingDayRepository workingDayRepository;
    private final PlanCalendarExceptionRepository exceptionRepository;
    private final ProjectPlanRepository planRepository;
    private final ProjectMemberRepository memberRepository;
    private final PlanCalendarMapper mapper;
    private final AuditService auditService;

    public PlanCalendarService(
            PlanCalendarRepository calendarRepository,
            PlanCalendarWorkingDayRepository workingDayRepository,
            PlanCalendarExceptionRepository exceptionRepository,
            ProjectPlanRepository planRepository,
            ProjectMemberRepository memberRepository,
            PlanCalendarMapper mapper,
            AuditService auditService) {
        this.calendarRepository = calendarRepository;
        this.workingDayRepository = workingDayRepository;
        this.exceptionRepository = exceptionRepository;
        this.planRepository = planRepository;
        this.memberRepository = memberRepository;
        this.mapper = mapper;
        this.auditService = auditService;
    }

    @Transactional
    public PlanCalendarResponse create(UserPrincipal actor, PlanCalendarCreateRequest request) {
        requireOrgAdmin(actor);

        if (request.parentCalendarId() != null) {
            findCalendar(request.parentCalendarId());
        }

        PlanCalendar calendar = new PlanCalendar();
        calendar.setName(request.name().trim());
        calendar.setDescription(request.description());
        calendar.setParentCalendarId(request.parentCalendarId());
        calendar.setOrganizationId(request.organizationId());
        calendar.setDailyWorkingHours(request.getDailyWorkingHoursOrDefault());
        calendar.setTimezone(request.timezone());
        calendar.setStatus(CalendarStatus.ACTIVE);

        calendarRepository.saveAndFlush(calendar);

        List<WorkingDayRequest> days = normalizeWorkingDays(request.workingDays());
        buildWorkingDays(calendar, days);

        auditService.record("PLAN_CALENDAR_CREATED", "PLAN_CALENDAR", calendar.getId(),
                Map.of("name", calendar.getName(), "organizationId", String.valueOf(calendar.getOrganizationId())));

        log.info("plan-calendar.create success id={} name={} actor={}",
                calendar.getId(), calendar.getName(), actor.getUsername());
        return toResponse(calendar);
    }

    public List<PlanCalendarResponse> list(UserPrincipal actor) {
        return calendarRepository.findByDeletedAtIsNull().stream()
                .map(this::toResponse)
                .collect(java.util.stream.Collectors.toList());
    }

    @Transactional
    public PlanCalendarResponse update(UserPrincipal actor, UUID calendarId, PlanCalendarUpdateRequest request) {
        requireOrgCalendarForMutation(actor, calendarId);

        PlanCalendar calendar = findCalendar(calendarId);

        if (!Objects.equals(calendar.getVersion(), request.version())) {
            throw new ConflictException("Record modified by another transaction");
        }

        calendar.setName(request.name().trim());
        calendar.setDescription(request.description());
        calendar.setDailyWorkingHours(request.getDailyWorkingHoursOrDefault());
        calendar.setTimezone(request.timezone());
        calendar.setStatus(request.status());

        calendarRepository.saveAndFlush(calendar);

        workingDayRepository.deleteByCalendarId(calendar.getId());
        workingDayRepository.flush();
        buildWorkingDays(calendar, normalizeWorkingDays(request.workingDays()));

        auditService.record("PLAN_CALENDAR_UPDATED", "PLAN_CALENDAR", calendar.getId(),
                Map.of("name", calendar.getName(), "status", String.valueOf(calendar.getStatus())));

        log.info("plan-calendar.update success id={} actor={}", calendarId, actor.getUsername());
        return toResponse(calendar);
    }

    @Transactional
    public void delete(UserPrincipal actor, UUID calendarId) {
        requireOrgCalendarForMutation(actor, calendarId);

        PlanCalendar calendar = findCalendar(calendarId);

        if (calendarRepository.existsByParentCalendarIdAndDeletedAtIsNull(calendarId)) {
            throw new BusinessException(ErrorCode.HAS_CHILDREN, "Calendar còn calendar con, không thể xóa");
        }
        if (planRepository.countByCalendarIdAndDeletedAtIsNull(calendarId) > 0) {
            throw new BusinessException(ErrorCode.HAS_CHILDREN, "Calendar đang được plan tham chiếu, không thể xóa");
        }

        calendar.setDeletedAt(java.time.Instant.now());
        calendar.setDeletedBy(actor.getId());
        calendarRepository.saveAndFlush(calendar);

        auditService.record("PLAN_CALENDAR_DELETED", "PLAN_CALENDAR", calendar.getId(),
                Map.of("name", calendar.getName()));
        log.info("plan-calendar.delete success id={} actor={}", calendarId, actor.getUsername());
    }

    @Transactional
    public PlanCalendarExceptionResponse addException(UserPrincipal actor, UUID calendarId,
            PlanCalendarExceptionRequest request) {
        requireOrgCalendarForMutation(actor, calendarId);

        PlanCalendar calendar = findCalendar(calendarId);

        exceptionRepository.findByCalendarIdAndExceptionDate(calendarId, request.exceptionDate())
                .ifPresent(e -> {
                    throw new ConflictException("Exception ngày " + request.exceptionDate() + " đã tồn tại");
                });

        PlanCalendarException exception = new PlanCalendarException();
        exception.setCalendar(calendar);
        exception.setExceptionDate(request.exceptionDate());
        exception.setExceptionType(request.exceptionType());
        exception.setNote(request.note());
        exceptionRepository.saveAndFlush(exception);

        auditService.record("PLAN_CALENDAR_EXCEPTION_CREATED", "PLAN_CALENDAR_EXCEPTION", exception.getId(),
                Map.of("date", String.valueOf(exception.getExceptionDate()),
                        "type", String.valueOf(exception.getExceptionType())));

        log.info("plan-calendar.exception.create success id={} date={} type={} actor={}",
                exception.getId(), exception.getExceptionDate(), exception.getExceptionType(), actor.getUsername());
        return mapper.toExceptionResponse(exception);
    }

    /**
     * Calendar hiệu dụng của plan (GET /plans/{id}/calendar) — PLN-AC-CAL-03.
     * Plan dùng calendarId; nếu null → fallback về system/org calendar (parent_calendar_id).
     */
    public PlanCalendarResponse effective(UUID planId, UserPrincipal actor) {
        ProjectPlan plan = planRepository.findByIdAndDeletedAtIsNull(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Kế hoạch", planId));
        checkProjectViewAccess(actor, plan.getProject().getId());

        PlanCalendar base = plan.getCalendarId() != null
                ? findCalendar(plan.getCalendarId())
                : findDefaultCalendar();

        List<PlanCalendar> chain = buildChain(base);

        Map<Integer, WorkingDayResponse> mergedDays = new LinkedHashMap<>();
        Map<LocalDate, PlanCalendarExceptionResponse> mergedExceptions = new LinkedHashMap<>();
        Integer dailyWorkingHours = null;
        String timezone = null;

        for (PlanCalendar c : chain) {
            for (PlanCalendarWorkingDay wd : workingDayRepository.findByCalendarId(c.getId())) {
                mergedDays.put(wd.getDayOfWeek(), mapper.toWorkingDayResponse(wd));
            }
            for (PlanCalendarException e : exceptionRepository.findByCalendarId(c.getId())) {
                mergedExceptions.put(e.getExceptionDate(), mapper.toExceptionResponse(e));
            }
            if (dailyWorkingHours == null && c.getDailyWorkingHours() != null) {
                dailyWorkingHours = c.getDailyWorkingHours();
            }
            if (timezone == null && c.getTimezone() != null) {
                timezone = c.getTimezone();
            }
        }

        List<WorkingDayResponse> workingDays = new ArrayList<>(mergedDays.values());
        workingDays.sort(Comparator.comparingInt(WorkingDayResponse::dayOfWeek));
        List<PlanCalendarExceptionResponse> exceptions = new ArrayList<>(mergedExceptions.values());
        exceptions.sort(Comparator.comparing(PlanCalendarExceptionResponse::exceptionDate));

        return new PlanCalendarResponse(
                base.getId(), base.getName(), base.getDescription(), base.getParentCalendarId(),
                base.getOrganizationId(), dailyWorkingHours, timezone, base.getStatus(),
                base.getVersion(), base.getCreatedAt(), workingDays, exceptions);
    }

    // ===================== helpers =====================

    /** Chuỗi kế thừa (fallback org): từ calendar gốc lên dần theo parent (root = calendar mặc định). */
    private List<PlanCalendar> buildChain(PlanCalendar calendar) {
        List<PlanCalendar> chain = new ArrayList<>();
        PlanCalendar cursor = calendar;
        java.util.HashSet<UUID> seen = new java.util.HashSet<>();
        while (cursor != null && !cursor.isDeleted()) {
            chain.add(0, cursor);
            if (!seen.add(cursor.getId()) || cursor.getParentCalendarId() == null) {
                break;
            }
            cursor = findCalendar(cursor.getParentCalendarId());
        }
        return chain;
    }

    private List<WorkingDayRequest> normalizeWorkingDays(List<WorkingDayRequest> input) {
        if (input == null || input.isEmpty()) {
            return defaultWorkingDays();
        }
        Map<Integer, WorkingDayRequest> uniq = new LinkedHashMap<>();
        for (WorkingDayRequest wd : input) {
            if (uniq.putIfAbsent(wd.dayOfWeek(), wd) != null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "dayOfWeek " + wd.dayOfWeek() + " bị lặp trong workingDays");
            }
        }
        return new ArrayList<>(uniq.values());
    }

    private void buildWorkingDays(PlanCalendar calendar, List<WorkingDayRequest> days) {
        for (WorkingDayRequest req : days) {
            PlanCalendarWorkingDay wd = mapper.toWorkingDayEntity(calendar, req);
            workingDayRepository.save(wd);
        }
        workingDayRepository.flush();
    }

    private PlanCalendar findCalendar(UUID calendarId) {
        return calendarRepository.findByIdAndDeletedAtIsNull(calendarId)
                .orElseThrow(() -> new ResourceNotFoundException("Working calendar", calendarId));
    }

    private PlanCalendar findDefaultCalendar() {
        return calendarRepository.findByDeletedAtIsNull().stream()
                .filter(c -> c.getStatus() == CalendarStatus.ACTIVE)
                .min(Comparator.comparing(PlanCalendar::getCreatedAt))
                .orElseThrow(() -> new ResourceNotFoundException("Working calendar",
                        "Chưa có calendar mặc định (system) cho kế hoạch"));
    }

    private PlanCalendarResponse toResponse(PlanCalendar calendar) {
        List<WorkingDayResponse> days = workingDayRepository.findByCalendarId(calendar.getId()).stream()
                .sorted(Comparator.comparingInt(PlanCalendarWorkingDay::getDayOfWeek))
                .map(mapper::toWorkingDayResponse)
                .collect(java.util.stream.Collectors.toList());
        List<PlanCalendarExceptionResponse> exceptions = exceptionRepository.findByCalendarId(calendar.getId()).stream()
                .sorted(Comparator.comparing(PlanCalendarException::getExceptionDate))
                .map(mapper::toExceptionResponse)
                .collect(java.util.stream.Collectors.toList());
        return mapper.toResponse(calendar, days, exceptions);
    }

    private void requireOrgCalendarForMutation(UserPrincipal actor, UUID calendarId) {
        requireOrgAdmin(actor);
        findCalendar(calendarId);
    }

    private void requireOrgAdmin(UserPrincipal actor) {
        if (!actor.getRoles().contains("ADMIN")) {
            throw new AccessDeniedException("Cần quyền ADMIN tổ chức để quản lý working calendar");
        }
    }

    private void checkProjectViewAccess(UserPrincipal actor, UUID projectId) {
        if (actor.getRoles().contains("ADMIN")) {
            return;
        }
        if (!memberRepository.existsByProjectIdAndUser_Id(projectId, actor.getId())) {
            throw new AccessDeniedException("Access denied to project");
        }
    }

    private List<WorkingDayRequest> defaultWorkingDays() {
        return List.of(
                workingDay(1), workingDay(2), workingDay(3), workingDay(4), workingDay(5),
                restDay(6), restDay(7));
    }

    private static WorkingDayRequest workingDay(int dayOfWeek) {
        return new WorkingDayRequest(dayOfWeek, true, DEFAULT_START, DEFAULT_END);
    }

    private static WorkingDayRequest restDay(int dayOfWeek) {
        return new WorkingDayRequest(dayOfWeek, false, null, null);
    }
}