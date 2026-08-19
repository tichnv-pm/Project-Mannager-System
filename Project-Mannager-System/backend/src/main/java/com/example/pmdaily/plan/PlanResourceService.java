package com.example.pmdaily.plan;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import com.example.pmdaily.exception.ConflictException;
import com.example.pmdaily.exception.ResourceNotFoundException;
import com.example.pmdaily.plan.dto.CapacityResponse;
import com.example.pmdaily.plan.dto.CapacityUpdateRequest;
import com.example.pmdaily.plan.dto.ResourceAssignmentRequest;
import com.example.pmdaily.plan.dto.ResourceAssignmentResponse;
import com.example.pmdaily.plan.dto.ResourceAssignmentUpdateRequest;
import com.example.pmdaily.plan.dto.ResourceOverviewRow;
import com.example.pmdaily.plan.dto.WorkloadBucket;
import com.example.pmdaily.plan.dto.WorkloadResponse;
import com.example.pmdaily.project.ProjectMember;
import com.example.pmdaily.project.ProjectMemberRepository;
import com.example.pmdaily.project.ProjectMemberRole;
import com.example.pmdaily.security.UserPrincipal;
import com.example.pmdaily.user.RoleRepository;
import com.example.pmdaily.user.User;
import com.example.pmdaily.user.UserRepository;

/**
 * Resource planning & workload (docs/planning/10, docs/api/13-planning-api.md muc 2.6) — PLN-FR-RES-*.
 * Rules: PLN-RULE-RES-01 (allocation 1..100), RES-02 (plannedEffort >= 0), RES-03 (over-allocation cảnh báo,
 * không leveling), RES-05 (USER/ROLE/EXTERNAL — TEAM loại), RES-06 (workload cross-plan);
 * summary gán được nhưng KHÔNG tính vào workload (docs/planning/10 muc 7 #2).
 */
@Service
@Transactional(readOnly = true)
public class PlanResourceService {

    private static final Logger log = LoggerFactory.getLogger(PlanResourceService.class);

    static final int DAILY_MINUTES = WorkingCalendar.DEFAULT_DAILY_MINUTES;

    private final PlanTaskResourceRepository resourceRepository;
    private final ResourceCapacityRepository capacityRepository;
    private final PlanTaskRepository taskRepository;
    private final ProjectPlanRepository planRepository;
    private final ProjectMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PlanCalendarRepository calendarRepository;
    private final PlanCalendarWorkingDayRepository workingDayRepository;
    private final PlanCalendarExceptionRepository exceptionRepository;
    private final AuditService auditService;
    private final PlanChangeHistoryService changeHistoryService;

    public PlanResourceService(PlanTaskResourceRepository resourceRepository,
            ResourceCapacityRepository capacityRepository,
            PlanTaskRepository taskRepository,
            ProjectPlanRepository planRepository,
            ProjectMemberRepository memberRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            PlanCalendarRepository calendarRepository,
            PlanCalendarWorkingDayRepository workingDayRepository,
            PlanCalendarExceptionRepository exceptionRepository,
            AuditService auditService,
            PlanChangeHistoryService changeHistoryService) {
        this.resourceRepository = resourceRepository;
        this.capacityRepository = capacityRepository;
        this.taskRepository = taskRepository;
        this.planRepository = planRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.calendarRepository = calendarRepository;
        this.workingDayRepository = workingDayRepository;
        this.exceptionRepository = exceptionRepository;
        this.auditService = auditService;
        this.changeHistoryService = changeHistoryService;
    }

    // ===================== gán / sửa / gỡ =====================

    @Transactional
    public ResourceAssignmentResponse assign(UserPrincipal actor, UUID planId, UUID taskId,
            ResourceAssignmentRequest request) {
        ProjectPlan plan = findPlan(planId);
        checkProjectManageAccess(actor, plan.getProject().getId());
        PlanTask task = findTask(planId, taskId);
        validateResourceExists(request.resourceType(), request.resourceId());

        if (resourceRepository.existsByTaskIdAndResourceTypeAndResourceId(
                taskId, request.resourceType(), request.resourceId())) {
            throw new ConflictException("Đã gán resource này vào task");
        }

        PlanTaskResource allocation = new PlanTaskResource();
        allocation.setPlan(plan);
        allocation.setTask(task);
        allocation.setResourceType(request.resourceType());
        allocation.setResourceId(request.resourceId());
        allocation.setAllocationPercent(request.allocationOrDefault());
        allocation.setRoleOnTask(request.roleOnTask());
        allocation.setStartDate(request.startDate());
        allocation.setEndDate(request.endDate());
        allocation.setPlannedEffortMinutes(request.plannedEffortMinutes());
        resourceRepository.saveAndFlush(allocation);

        changeHistoryService.record(actor, plan, "PLAN_RESOURCE_ADDED", "PLAN_TASK_RESOURCE",
                allocation.getId(), request.resourceType().name() + "/" + request.resourceId(),
                null, allocation.getAllocationPercent(), null);

        auditService.record("PLAN_RESOURCE_ASSIGNED", "PLAN_TASK_RESOURCE", allocation.getId(),
                Map.of("planId", String.valueOf(planId), "taskCode", task.getTaskCode(),
                        "resourceType", request.resourceType().name(),
                        "resourceId", String.valueOf(request.resourceId()),
                        "allocationPercent", allocation.getAllocationPercent()));

        log.info("plan-resource.assign success id={} task={} type={} actor={}",
                allocation.getId(), task.getTaskCode(), request.resourceType(), actor.getUsername());
        return toResponse(allocation);
    }

    @Transactional
    public ResourceAssignmentResponse update(UserPrincipal actor, UUID allocationId,
            ResourceAssignmentUpdateRequest request) {
        PlanTaskResource allocation = resourceRepository.findById(allocationId)
                .orElseThrow(() -> new ResourceNotFoundException("Resource assignment", allocationId));
        checkProjectManageAccess(actor, allocation.getPlan().getProject().getId());

        int oldPercent = allocation.getAllocationPercent();

        if (request.allocationPercent() != null) {
            allocation.setAllocationPercent(request.allocationPercent());
        }
        if (request.roleOnTask() != null) {
            allocation.setRoleOnTask(request.roleOnTask());
        }
        if (request.startDate() != null) {
            allocation.setStartDate(request.startDate());
        }
        if (request.endDate() != null) {
            allocation.setEndDate(request.endDate());
        }
        if (request.plannedEffortMinutes() != null) {
            allocation.setPlannedEffortMinutes(request.plannedEffortMinutes());
        }
        resourceRepository.save(allocation);

        changeHistoryService.record(actor, allocation.getPlan(), "PLAN_RESOURCE_UPDATED", "PLAN_TASK_RESOURCE",
                allocation.getId(), "allocationPercent", oldPercent,
                request.allocationPercent() == null ? null : request.allocationPercent(),
                null);

        auditService.record("PLAN_RESOURCE_UPDATED", "PLAN_TASK_RESOURCE", allocation.getId(),
                Map.of("allocationPercent", allocation.getAllocationPercent(),
                        "startDate", String.valueOf(allocation.getStartDate()),
                        "endDate", String.valueOf(allocation.getEndDate())));

        log.info("plan-resource.update success id={} actor={}", allocationId, actor.getUsername());
        return toResponse(allocation);
    }

    @Transactional
    public void remove(UserPrincipal actor, UUID allocationId) {
        PlanTaskResource allocation = resourceRepository.findById(allocationId)
                .orElseThrow(() -> new ResourceNotFoundException("Resource assignment", allocationId));
        checkProjectManageAccess(actor, allocation.getPlan().getProject().getId());

        resourceRepository.delete(allocation);
        resourceRepository.flush();

        changeHistoryService.record(actor, allocation.getPlan(), "PLAN_RESOURCE_REMOVED", "PLAN_TASK_RESOURCE",
                allocation.getId(), "allocationPercent", allocation.getAllocationPercent(), null, null);

        auditService.record("PLAN_RESOURCE_REMOVED", "PLAN_TASK_RESOURCE", allocation.getId(),
                Map.of("taskCode", allocation.getTask().getTaskCode(),
                        "resourceType", allocation.getResourceType().name(),
                        "resourceId", String.valueOf(allocation.getResourceId())));

        log.info("plan-resource.remove success id={} actor={}", allocationId, actor.getUsername());
    }

    @Transactional
    public CapacityResponse upsertCapacity(UUID actorId, UUID resourceId, CapacityUpdateRequest request) {
        if (request.resourceType() != ResourceType.USER && request.resourceType() != ResourceType.ROLE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        ResourceCapacity capacity = capacityRepository
                .findByResourceTypeAndResourceIdAndStartDate(request.resourceType(), resourceId, request.startDate())
                .orElseGet(() -> {
                    ResourceCapacity c = new ResourceCapacity();
                    c.setResourceType(request.resourceType());
                    c.setResourceId(resourceId);
                    c.setStartDate(request.startDate());
                    return c;
                });
        capacity.setCapacityPercent(request.capacityPercent());
        capacity.setEndDate(request.endDate());
        capacity.setSource(request.source() == null ? CapacitySource.ORG : request.source());
        capacity.setCapacityPercent(request.capacityPercent());
        capacityRepository.save(capacity);

        auditService.record("RESOURCE_CAPACITY_UPSERTED", "RESOURCE_CAPACITY", capacity.getId(),
                Map.of("resourceType", capacity.getResourceType().name(),
                        "resourceId", String.valueOf(capacity.getResourceId()),
                        "capacityPercent", capacity.getCapacityPercent(),
                        "startDate", String.valueOf(capacity.getStartDate())));

        log.info("resource-capacity.upsert success id={} type={} actor={}",
                capacity.getId(), capacity.getResourceType(), actorId);
        return toCapacityResponse(capacity);
    }

    // ===================== workload =====================

    public List<WorkloadResponse> planWorkload(UserPrincipal actor, UUID planId, LocalDate from, LocalDate to,
            WorkloadGranularity granularity) {
        findPlan(planId);
        boolean restrictOwn = !actor.getPermissions().contains("plan:resource");
        Map<ResourceKey, List<PlanTaskResource>> byResource = new LinkedHashMap<>();
        for (PlanTaskResource r : resourceRepository.findByPlanId(planId)) {
            if (restrictOwn
                    && !(r.getResourceType() == ResourceType.USER && r.getResourceId().equals(actor.getId()))) {
                continue;
            }
            byResource.computeIfAbsent(new ResourceKey(r.getResourceType(), r.getResourceId()), k -> new ArrayList<>())
                    .add(r);
        }
        List<WorkloadResponse> result = new ArrayList<>();
        for (Map.Entry<ResourceKey, List<PlanTaskResource>> entry : byResource.entrySet()) {
            result.add(computeWorkload(entry.getKey().type(), entry.getKey().resourceId(), entry.getValue(),
                    from, to, granularity));
        }
        return result;
    }

    /** Tổng hợp over-allocation cross-plan (docs/planning/10 muc 4) — chỉ đếm plan APPROVED/ACTIVE. */
    public List<ResourceOverviewRow> overview(LocalDate from, LocalDate to) {
        Map<ResourceKey, List<PlanTaskResource>> byResource = new LinkedHashMap<>();
        for (PlanTaskResource r : resourceRepository.findAll()) {
            PlanStatus status = r.getPlan().getStatus();
            if (status != PlanStatus.APPROVED && status != PlanStatus.ACTIVE) {
                continue;
            }
            byResource.computeIfAbsent(new ResourceKey(r.getResourceType(), r.getResourceId()), k -> new ArrayList<>())
                    .add(r);
        }
        List<ResourceOverviewRow> rows = new ArrayList<>();
        for (Map.Entry<ResourceKey, List<PlanTaskResource>> entry : byResource.entrySet()) {
            ResourceKey key = entry.getKey();
            long demand = 0;
            for (PlanTaskResource r : entry.getValue()) {
                demand += demandInRange(r, from, to);
            }
            Integer capacity = key.type() == ResourceType.EXTERNAL ? null
                    : capacityInRange(key.type(), key.resourceId(), from, to);
            Double utilization = capacity == null || capacity == 0 ? null : percent(demand, capacity);
            boolean over = capacity != null && demand > capacity;
            rows.add(new ResourceOverviewRow(key.type(), key.resourceId(),
                    resourceName(key.type(), key.resourceId()), demand, capacity, utilization, over));
        }
        return rows;
    }

    private WorkloadResponse computeWorkload(ResourceType type, UUID resourceId, List<PlanTaskResource> rows,
            LocalDate from, LocalDate to, WorkloadGranularity granularity) {
        List<WorkloadBucket> buckets = new ArrayList<>();
        long totalDemand = 0;
        long totalCapacity = 0;
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            LocalDate bucketStart = bucketStart(cursor, granularity);
            LocalDate bucketEnd = bucketEnd(bucketStart, granularity);
            if (bucketEnd.isAfter(to)) {
                bucketEnd = to;
            }
            long demand = 0;
            for (PlanTaskResource r : rows) {
                demand += demandInRange(r, bucketStart, bucketEnd);
            }
            Integer capacity = null;
            if (type != ResourceType.EXTERNAL) {
                capacity = capacityInRange(type, resourceId, bucketStart, bucketEnd);
            }
            Double utilization = capacity == null || capacity == 0 ? null : percent(demand, capacity);
            boolean over = capacity != null && demand > capacity;
            buckets.add(new WorkloadBucket(bucketStart, demand, capacity, utilization, over));
            totalDemand += demand;
            if (capacity != null) {
                totalCapacity += capacity;
            }
            cursor = bucketEnd.plusDays(1);
        }
        Double totalUtil = totalCapacity == 0 ? null : percent(totalDemand, totalCapacity);
        boolean totalOver = totalCapacity > 0 && totalDemand > totalCapacity;
        return new WorkloadResponse(type, resourceId, resourceName(type, resourceId), granularity.name(),
                from, to, totalDemand, type == ResourceType.EXTERNAL ? null : (int) totalCapacity,
                totalUtil, totalOver, buckets);
    }

    // ===================== demand / capacity =====================

    /** Phút resource dồn cho 1 ngày làm việc của task (summary bị loại, RES muc 7 #2). */
    private long demandForDay(PlanTaskResource r, LocalDate day) {
        PlanTask task = r.getTask();
        if (task.isSummary() || task.getPlannedStart() == null || task.getPlannedFinish() == null) {
            return 0;
        }
        if (day.isBefore(task.getPlannedStart()) || day.isAfter(task.getPlannedFinish())) {
            return 0;
        }
        if (r.getStartDate() != null && day.isBefore(r.getStartDate())) {
            return 0;
        }
        if (r.getEndDate() != null && day.isAfter(r.getEndDate())) {
            return 0;
        }
        WorkingCalendar calendar = WorkingCalendar.build(task.getPlan(), calendarRepository,
                workingDayRepository, exceptionRepository);
        if (task.getDurationMinutes() == null || task.getDurationMinutes() <= 0) {
            return 0;
        }
        long days = calendar.workingDaysInSpan(task.getPlannedStart(), task.getPlannedFinish());
        if (days <= 0) {
            return 0;
        }
        return Math.round(task.getDurationMinutes() * (double) r.getAllocationPercent() / 100.0 / days);
    }

    private long demandInRange(PlanTaskResource r, LocalDate from, LocalDate to) {
        long total = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            total += demandForDay(r, d);
        }
        return total;
    }

    private Integer capacityInRange(ResourceType type, UUID resourceId, LocalDate from, LocalDate to) {
        long total = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (d.getDayOfWeek().getValue() <= 5) {
                total += effectiveCapacityPercent(type, resourceId, d) * DAILY_MINUTES / 100;
            }
        }
        return (int) total;
    }

    /** Capacity % hiệu dụng cho 1 ngày: khai báo hoặc mặc định 100 (docs/planning/10 muc 3). */
    private int effectiveCapacityPercent(ResourceType type, UUID resourceId, LocalDate day) {
        int base = 100;
        for (ResourceCapacity c : capacityRepository.findByResourceTypeAndResourceIdOrderByStartDate(type,
                resourceId)) {
            if (!c.getStartDate().isAfter(day)
                    && (c.getEndDate() == null || !c.getEndDate().isBefore(day))) {
                base = c.getCapacityPercent();
            }
        }
        return base;
    }

    private static LocalDate bucketStart(LocalDate day, WorkloadGranularity granularity) {
        return switch (granularity) {
            case WEEK -> day.minusDays(day.getDayOfWeek().getValue() - 1L);
            case MONTH -> day.withDayOfMonth(1);
            case DAY -> day;
        };
    }

    private static LocalDate bucketEnd(LocalDate bucketStart, WorkloadGranularity granularity) {
        return switch (granularity) {
            case WEEK -> bucketStart.plusDays(6);
            case MONTH -> bucketStart.plusDays(bucketStart.lengthOfMonth() - 1);
            case DAY -> bucketStart;
        };
    }

    private Double percent(long demand, long capacity) {
        return BigDecimal.valueOf(demand)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(capacity), 1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private String resourceName(ResourceType type, UUID resourceId) {
        if (type == ResourceType.USER) {
            return userRepository.findById(resourceId).map(User::getFullName).orElse(null);
        }
        if (type == ResourceType.ROLE) {
            return roleRepository.findById(resourceId).map(role -> role.getName()).orElse(null);
        }
        return "External";
    }

    private void validateResourceExists(ResourceType type, UUID resourceId) {
        if (type == ResourceType.USER && !userRepository.existsById(resourceId)) {
            throw new ResourceNotFoundException("Người dùng", resourceId);
        }
        if (type == ResourceType.ROLE && !roleRepository.existsById(resourceId)) {
            throw new ResourceNotFoundException("Vai trò", resourceId);
        }
    }

    private ResourceAssignmentResponse toResponse(PlanTaskResource allocation) {
        ResourceType type = allocation.getResourceType();
        OverStatus status = overAllocation(allocation, type);
        return new ResourceAssignmentResponse(allocation.getId(), allocation.getPlan().getId(),
                allocation.getTask().getId(), allocation.getTask().getTaskCode(),
                allocation.getTask().getTaskName(), allocation.getTask().isSummary(),
                type, allocation.getResourceId(), resourceName(type, allocation.getResourceId()),
                allocation.getRoleOnTask(), allocation.getAllocationPercent(),
                allocation.getStartDate(), allocation.getEndDate(),
                allocation.getPlannedEffortMinutes(), status.over, status.utilization);
    }

    /** Tính lại demand resource trong cửa sổ windows của allocation -> cảnh báo over (PLN-RULE-RES-03). */
    private OverStatus overAllocation(PlanTaskResource allocation, ResourceType type) {
        PlanTask task = allocation.getTask();
        if (task.getPlannedStart() == null || task.getPlannedFinish() == null) {
            return OverStatus.of(false, null);
        }
        LocalDate from = allocation.getStartDate() == null ? task.getPlannedStart()
                : maxOfAllocation(task.getPlannedStart(), allocation.getStartDate());
        LocalDate to = allocation.getEndDate() == null ? task.getPlannedFinish()
                : minOfAllocation(task.getPlannedFinish(), allocation.getEndDate());
        if (from.isAfter(to)) {
            return OverStatus.of(false, null);
        }
        long demand = 0;
        for (PlanTaskResource r : resourceRepository
                .findByResourceTypeAndResourceId(type, allocation.getResourceId())) {
            demand += demandInRange(r, from, to);
        }
        if (type == ResourceType.EXTERNAL) {
            return OverStatus.of(false, null);
        }
        Integer capacity = capacityInRange(type, allocation.getResourceId(), from, to);
        if (capacity == null || capacity <= 0) {
            return OverStatus.of(false, null);
        }
        Double utilization = percent(demand, capacity);
        return OverStatus.of(demand > capacity, utilization);
    }

    private static LocalDate maxOfAllocation(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate minOfAllocation(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
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

    /**
     * Danh sách resource assignment của plan (cho UI Resource tab — bổ sung read-only
     * GET /plans/{id}/resources, docs/api/13-planning-api.md mục 2.6, PLN-FE-06).
     */
    public List<ResourceAssignmentResponse> listPlanResources(UserPrincipal actor, UUID planId) {
        ProjectPlan plan = findPlan(planId);
        checkProjectViewAccess(actor, plan.getProject().getId());
        return resourceRepository.findByPlanId(planId).stream()
                .sorted(java.util.Comparator.comparing(a -> a.getTask().getTaskCode()))
                .map(this::toResponse)
                .toList();
    }

    private void checkProjectViewAccess(UserPrincipal actor, UUID projectId) {
        if (actor.getRoles().contains("ADMIN")) {
            return;
        }
        if (!memberRepository.existsByProjectIdAndUser_Id(projectId, actor.getId())) {
            throw new AccessDeniedException("Access denied to project");
        }
    }

    private void checkWorkloadView(UserPrincipal actor, UUID resourceId) {
        if (!actor.getPermissions().contains("plan:resource")
                && !resourceId.equals(actor.getId())) {
            throw new AccessDeniedException("MEMBER chỉ xem được workload của chính mình");
        }
    }

    private CapacityResponse toCapacityResponse(ResourceCapacity c) {
        return new CapacityResponse(c.getId(), c.getResourceType(), c.getResourceId(), c.getCapacityPercent(),
                c.getStartDate(), c.getEndDate(), c.getSource());
    }

    private ProjectPlan findPlan(UUID planId) {
        return planRepository.findByIdAndDeletedAtIsNull(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Kế hoạch", planId));
    }

    private PlanTask findTask(UUID planId, UUID taskId) {
        return taskRepository.findByIdAndPlanIdAndDeletedAtIsNull(taskId, planId)
                .orElseThrow(() -> new ResourceNotFoundException("Planning task", taskId));
    }

    public WorkloadResponse workload(UserPrincipal actor, UUID resourceId, LocalDate from, LocalDate to,
            WorkloadGranularity granularity) {
        checkWorkloadView(actor, resourceId);
        List<PlanTaskResource> rows = resourceRepository.findByResourceId(resourceId);
        ResourceType type = rows.isEmpty() ? ResourceType.USER : rows.get(0).getResourceType();
        return computeWorkload(type, resourceId, rows, from, to, granularity);
    }

    private record ResourceKey(ResourceType type, UUID resourceId) {
    }

    private record OverStatus(boolean over, Double utilization) {
        static OverStatus of(boolean over, Double utilization) {
            return new OverStatus(over, utilization);
        }
    }
}