package com.example.pmdaily.plan;

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

import com.example.pmdaily.common.PageResponse;
import com.example.pmdaily.plan.dto.CriticalPathResult;
import com.example.pmdaily.plan.dto.PlanCalendarResponse;
import com.example.pmdaily.plan.dto.PlanCreateRequest;
import com.example.pmdaily.plan.dto.PlanResponse;
import com.example.pmdaily.plan.dto.PlanUpdateRequest;
import com.example.pmdaily.plan.dto.RecalcResponse;
import com.example.pmdaily.security.UserPrincipal;

import jakarta.validation.Valid;

/**
 * REST API Project Plan (docs/api/13-planning-api.md muc 2.1, 3.1) — PLN-FR-PLAN-*.
 * Gantt / critical-path được triển khai ở PLN-BE-05/06 (scheduling & critical path).
 */
@RestController
@RequestMapping("/api/v1/plans")
public class PlanController {

    private final PlanService planService;
    private final PlanCalendarService calendarService;
    private final PlanScheduleService scheduleService;
    private final CriticalPathService criticalPathService;

    public PlanController(PlanService planService, PlanCalendarService calendarService,
            PlanScheduleService scheduleService, CriticalPathService criticalPathService) {
        this.planService = planService;
        this.calendarService = calendarService;
        this.scheduleService = scheduleService;
        this.criticalPathService = criticalPathService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('plan:view')")
    public PageResponse<PlanResponse> list(
            @AuthenticationPrincipal UserPrincipal actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) PlanType planType,
            @RequestParam(required = false) PlanStatus status) {
        return planService.search(actor, keyword, projectId, planType, status, page, size, sort);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('plan:create')")
    public PlanResponse create(
            @AuthenticationPrincipal UserPrincipal actor,
            @Valid @RequestBody PlanCreateRequest request) {
        return planService.create(actor, request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('plan:view')")
    public PlanResponse get(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id) {
        return planService.get(id, actor);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('plan:update')")
    public PlanResponse update(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id,
            @Valid @RequestBody PlanUpdateRequest request) {
        return planService.update(actor, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('plan:delete')")
    public void delete(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id) {
        planService.delete(actor, id);
    }

    @GetMapping("/{id}/calendar")
    @PreAuthorize("hasAuthority('plan:view')")
    public PlanCalendarResponse effectiveCalendar(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id) {
        return calendarService.effective(id, actor);
    }

    @PostMapping("/{id}/recalc")
    @PreAuthorize("hasAuthority('plan:schedule')")
    public RecalcResponse recalculate(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id) {
        return scheduleService.recalculate(actor, id);
    }

    @GetMapping("/{id}/critical-path")
    @PreAuthorize("hasAuthority('plan:view')")
    public CriticalPathResult criticalPath(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id) {
        return criticalPathService.calculate(id);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('plan:update')")
    public PlanResponse submit(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id) {
        return planService.submit(actor, id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('plan:approve')")
    public PlanResponse approve(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id) {
        return planService.approve(actor, id);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('plan:approve')")
    public PlanResponse activate(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id) {
        return planService.activate(actor, id);
    }
}
