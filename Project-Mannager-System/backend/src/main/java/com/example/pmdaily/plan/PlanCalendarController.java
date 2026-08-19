package com.example.pmdaily.plan;

import java.util.List;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.pmdaily.plan.dto.PlanCalendarCreateRequest;
import com.example.pmdaily.plan.dto.PlanCalendarExceptionRequest;
import com.example.pmdaily.plan.dto.PlanCalendarExceptionResponse;
import com.example.pmdaily.plan.dto.PlanCalendarResponse;
import com.example.pmdaily.plan.dto.PlanCalendarUpdateRequest;
import com.example.pmdaily.security.UserPrincipal;

import jakarta.validation.Valid;

/**
 * REST API Working Calendar (docs/api/13-planning-api.md muc 2.4) — PLN-FR-CAL-*.
 */
@RestController
@RequestMapping("/api/v1/plan-calendars")
public class PlanCalendarController {

    private final PlanCalendarService calendarService;

    public PlanCalendarController(PlanCalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('plan:update')")
    public PlanCalendarResponse create(
            @AuthenticationPrincipal UserPrincipal actor,
            @Valid @RequestBody PlanCalendarCreateRequest request) {
        return calendarService.create(actor, request);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('plan:view')")
    public List<PlanCalendarResponse> list(@AuthenticationPrincipal UserPrincipal actor) {
        return calendarService.list(actor);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('plan:update')")
    public PlanCalendarResponse update(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id,
            @Valid @RequestBody PlanCalendarUpdateRequest request) {
        return calendarService.update(actor, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('plan:update')")
    public void delete(@AuthenticationPrincipal UserPrincipal actor, @PathVariable UUID id) {
        calendarService.delete(actor, id);
    }

    @PostMapping("/{id}/exceptions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('plan:update')")
    public PlanCalendarExceptionResponse addException(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id,
            @Valid @RequestBody PlanCalendarExceptionRequest request) {
        return calendarService.addException(actor, id, request);
    }
}