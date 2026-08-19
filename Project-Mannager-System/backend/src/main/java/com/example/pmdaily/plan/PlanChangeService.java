package com.example.pmdaily.plan;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.pmdaily.audit.AuditService;
import com.example.pmdaily.common.ErrorCode;
import com.example.pmdaily.exception.BusinessException;
import com.example.pmdaily.exception.ResourceNotFoundException;
import com.example.pmdaily.plan.dto.ChangeHistoryResponse;
import com.example.pmdaily.plan.dto.ChangeSuggestionCreateRequest;
import com.example.pmdaily.plan.dto.ChangeSuggestionResponse;
import com.example.pmdaily.plan.dto.SuggestionChangeField;
import com.example.pmdaily.project.ProjectMember;
import com.example.pmdaily.project.ProjectMemberRepository;
import com.example.pmdaily.project.ProjectMemberRole;
import com.example.pmdaily.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Change history & suggestion (docs/api/13-planning-api.md muc 2.9, docs/planning/02 muc 2.10).
 * PLN-AC-CHG-01/02/03/04; PLN-AC-CHG-02b: tổng effort plan >= 10.000 phút -> cần 2 người duyệt (PM + ADMIN)
 * trước khi APPLIED; PLN-RULE-CHG-05 cấm ghi đè baseline.
 */
@Service
@Transactional(readOnly = true)
public class PlanChangeService {

    private static final Logger log = LoggerFactory.getLogger(PlanChangeService.class);

    static final int DUAL_APPROVE_EFFORT_THRESHOLD = 10_000;

    private final PlanChangeRequestRepository requestRepository;
    private final PlanChangeHistoryRepository historyRepository;
    private final ProjectPlanRepository planRepository;
    private final PlanTaskRepository taskRepository;
    private final ProjectMemberRepository memberRepository;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final PlanChangeHistoryService changeHistoryService;

    public PlanChangeService(PlanChangeRequestRepository requestRepository,
            PlanChangeHistoryRepository historyRepository,
            ProjectPlanRepository planRepository,
            PlanTaskRepository taskRepository,
            ProjectMemberRepository memberRepository,
            ObjectMapper objectMapper,
            AuditService auditService,
            PlanChangeHistoryService changeHistoryService) {
        this.requestRepository = requestRepository;
        this.historyRepository = historyRepository;
        this.planRepository = planRepository;
        this.taskRepository = taskRepository;
        this.memberRepository = memberRepository;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.changeHistoryService = changeHistoryService;
    }

    // ===================== history =====================

    @Transactional(readOnly = true)
    public List<ChangeHistoryResponse> listHistories(UUID planId) {
        findPlan(planId);
        return historyRepository.findByPlanIdAndDeletedAtIsNullOrderByChangedAtDesc(planId)
                .stream().map(h -> new ChangeHistoryResponse(h.getId(), h.getPlan().getId(),
                        h.getChangeType(), h.getEntityType(), h.getEntityId(), h.getFieldChanged(),
                        h.getOldValue(), h.getNewValue(), h.getReason(), h.getChangeRequestId(),
                        h.getChangedBy(), h.getChangedAt()))
                .toList();
    }

    // ===================== suggestion =====================

    /**
     * Danh sách change suggestion của plan (cho UI Change tab — bổ sung read-only
     * GET /plans/{id}/change-suggestions, docs/api/13-planning-api.md mục 2.9, PLN-FE-08).
     */
    @Transactional(readOnly = true)
    public List<ChangeSuggestionResponse> listSuggestions(UserPrincipal actor, UUID planId) {
        ProjectPlan plan = findPlan(planId);
        checkProjectViewAccess(actor, plan.getProject().getId());
        return requestRepository.findByPlanIdAndDeletedAtIsNullOrderByCreatedAtDesc(planId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public ChangeSuggestionResponse createSuggestion(UserPrincipal actor, UUID planId,
            ChangeSuggestionCreateRequest request) {
        ProjectPlan plan = findPlan(planId);
        checkProjectManageAccess(actor, plan.getProject().getId());

        PlanChangeRequest suggestion = new PlanChangeRequest();
        suggestion.setPlan(plan);
        suggestion.setSourceType(request.sourceType());
        suggestion.setSourceId(request.sourceId());
        suggestion.setTitle(request.title());
        suggestion.setDescription(request.description());
        suggestion.setSuggestedChanges(writeJson(request.suggestedChanges()));
        suggestion.setStatus(PlanChangeRequestStatus.PENDING);
        requestRepository.saveAndFlush(suggestion);

        auditService.record("PLAN_CHANGE_REQUESTED", "PLAN_CHANGE_REQUEST", suggestion.getId(),
                Map.of("planId", String.valueOf(planId), "title", suggestion.getTitle()));

        log.info("plan-change.create suggestion id={} plan={} actor={}", suggestion.getId(), planId,
                actor.getUsername());
        return toResponse(suggestion);
    }

    @Transactional
    public ChangeSuggestionResponse accept(UserPrincipal actor, UUID suggestionId) {
        PlanChangeRequest suggestion = findSuggestion(suggestionId);
        checkProjectManageAccess(actor, suggestion.getPlan().getProject().getId());
        requirePending(suggestion);

        boolean dual = totalEffortMinutes(suggestion.getPlan().getId()) >= DUAL_APPROVE_EFFORT_THRESHOLD;
        boolean applied = false;

        if (dual && suggestion.getReviewedBy() == null) {
            suggestion.setReviewedBy(actor.getId());
            suggestion.setReviewedAt(Instant.now());
            auditService.record("PLAN_CHANGE_REVIEWED", "PLAN_CHANGE_REQUEST", suggestion.getId(),
                    Map.of("approvedBy", String.valueOf(actor.getId()), "approval", "1/2"));
        } else if (dual && suggestion.getReviewedBy2() == null) {
            if (suggestion.getReviewedBy() != null && suggestion.getReviewedBy().equals(actor.getId())) {
                throw new BusinessException(ErrorCode.CONFLICT,
                        "Chính người duyệt đầu tiên không thể duyệt lượt thứ hai");
            }
            suggestion.setReviewedBy2(actor.getId());
            suggestion.setReviewedAt2(Instant.now());
            suggestion.setStatus(PlanChangeRequestStatus.APPLIED);
            applied = true;
        } else {
            suggestion.setReviewedBy(actor.getId());
            suggestion.setReviewedAt(Instant.now());
            suggestion.setStatus(PlanChangeRequestStatus.APPLIED);
            applied = true;
        }
        requestRepository.saveAndFlush(suggestion);

        if (applied) {
            applyChanges(actor, suggestion);
            auditService.record("PLAN_CHANGE_APPLIED", "PLAN_CHANGE_REQUEST", suggestion.getId(),
                    Map.of("planId", String.valueOf(suggestion.getPlan().getId())));
            log.info("plan-change.accept applied id={} actor={}", suggestionId, actor.getUsername());
        }
        return toResponse(suggestion);
    }

    @Transactional
    public ChangeSuggestionResponse reject(UserPrincipal actor, UUID suggestionId) {
        PlanChangeRequest suggestion = findSuggestion(suggestionId);
        checkProjectManageAccess(actor, suggestion.getPlan().getProject().getId());
        if (suggestion.getStatus() == PlanChangeRequestStatus.APPLIED) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Suggestion đã được duyệt");
        }
        if (suggestion.getStatus() == PlanChangeRequestStatus.REJECTED) {
            throw new BusinessException(ErrorCode.ALREADY_LINKED, "Suggestion đã bị từ chối");
        }
        suggestion.setStatus(PlanChangeRequestStatus.REJECTED);
        suggestion.setReviewedBy(actor.getId());
        suggestion.setReviewedAt(Instant.now());
        requestRepository.saveAndFlush(suggestion);

        auditService.record("PLAN_CHANGE_REJECTED", "PLAN_CHANGE_REQUEST", suggestion.getId(),
                Map.of("planId", String.valueOf(suggestion.getPlan().getId())));
        log.info("plan-change.reject suggestion id={} actor={}", suggestionId, actor.getUsername());
        return toResponse(suggestion);
    }

    // ===================== apply =====================

    private void applyChanges(UserPrincipal actor, PlanChangeRequest suggestion) {
        List<SuggestionChangeField> changes = readJson(suggestion.getSuggestedChanges());
        if (changes.isEmpty()) {
            return;
        }
        for (SuggestionChangeField change : changes) {
            if (!"PLAN_TASK".equalsIgnoreCase(change.entityType())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Chỉ hỗ trợ entityType PLAN_TASK");
            }
            PlanTask task = taskRepository.findByIdAndPlanIdAndDeletedAtIsNull(
                            change.entityId(), suggestion.getPlan().getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR,
                            "Task đề xuất không thuộc plan: " + change.entityId()));

            String oldValue = oldValueOrNull(task, change.field());
            applyField(task, change.field(), change.newValue());
            taskRepository.saveAndFlush(task);
            changeHistoryService.record(actor, suggestion.getPlan(), "SUGGESTION_APPLIED", "PLAN_TASK",
                    task.getId(), change.field(), oldValue, change.newValue(),
                    suggestion.getTitle(), suggestion.getId());
        }
    }

    private void applyField(PlanTask task, String field, String newValue) {
        try {
            switch (field) {
                case "plannedStart" -> task.setPlannedStart(LocalDate.parse(newValue));
                case "plannedFinish" -> task.setPlannedFinish(LocalDate.parse(newValue));
                case "durationMinutes" -> task.setDurationMinutes(Long.valueOf(newValue));
                case "plannedEffortMinutes" -> task.setPlannedEffortMinutes(Integer.valueOf(newValue));
                case "percentComplete" -> {
                    int percent = Integer.parseInt(newValue);
                    if (percent < 0 || percent > 100) {
                        throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                                "percentComplete phải nằm trong 0..100");
                    }
                    task.setPercentComplete(percent);
                }
                case "status" -> task.setStatus(PlanTaskStatus.valueOf(newValue.toUpperCase()));
                default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Không hỗ trợ field: " + field);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Giá trị không hợp lệ cho field " + field + ": " + newValue);
        }
    }

    private String oldValueOrNull(PlanTask task, String field) {
        try {
            switch (field) {
                case "plannedStart" -> {
                    return String.valueOf(task.getPlannedStart());
                }
                case "plannedFinish" -> {
                    return String.valueOf(task.getPlannedFinish());
                }
                case "durationMinutes" -> {
                    return String.valueOf(task.getDurationMinutes());
                }
                case "plannedEffortMinutes" -> {
                    return String.valueOf(task.getPlannedEffortMinutes());
                }
                case "percentComplete" -> {
                    return String.valueOf(task.getPercentComplete());
                }
                case "status" -> {
                    return String.valueOf(task.getStatus());
                }
                default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Không hỗ trợ field: " + field);
            }
        } catch (BusinessException e) {
            throw e;
        }
    }

    private long totalEffortMinutes(UUID planId) {
        return taskRepository.findByPlanIdAndDeletedAtIsNull(planId).stream()
                .mapToLong(t -> t.getPlannedEffortMinutes() == null ? 0L : t.getPlannedEffortMinutes())
                .sum();
    }

    // ===================== helpers =====================

    private String writeJson(List<SuggestionChangeField> changes) {
        try {
            return objectMapper.writeValueAsString(changes);
        } catch (Exception e) {
            throw new IllegalStateException("Không tạo được suggested_changes", e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<SuggestionChangeField> readJson(String json) {
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class,
                            SuggestionChangeField.class));
        } catch (Exception e) {
            throw new IllegalStateException("suggested_changes không đọc được", e);
        }
    }

    private PlanChangeRequest findSuggestion(UUID suggestionId) {
        return requestRepository.findByIdAndDeletedAtIsNull(suggestionId)
                .orElseThrow(() -> new ResourceNotFoundException("Change suggestion", suggestionId));
    }

    private void requirePending(PlanChangeRequest suggestion) {
        if (suggestion.getStatus() != PlanChangeRequestStatus.PENDING) {
            throw new BusinessException(ErrorCode.ALREADY_LINKED,
                    "Suggestion đã được xử lý (APPLIED/REJECTED)");
        }
    }

    private ProjectPlan findPlan(UUID planId) {
        return planRepository.findByIdAndDeletedAtIsNull(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Kế hoạch", planId));
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

    private void checkProjectViewAccess(UserPrincipal actor, UUID projectId) {
        if (actor.getRoles().contains("ADMIN")) {
            return;
        }
        if (!memberRepository.existsByProjectIdAndUser_Id(projectId, actor.getId())) {
            throw new AccessDeniedException("Access denied to project");
        }
    }

    private ChangeSuggestionResponse toResponse(PlanChangeRequest suggestion) {
        return new ChangeSuggestionResponse(suggestion.getId(), suggestion.getPlan().getId(),
                suggestion.getSourceType(), suggestion.getSourceId(), suggestion.getTitle(),
                suggestion.getDescription(), suggestion.getStatus().name(), suggestion.getReviewedBy(),
                suggestion.getReviewedAt(), suggestion.getReviewedBy2(), suggestion.getReviewedAt2(),
                suggestion.getCreatedBy(), suggestion.getCreatedAt());
    }
}