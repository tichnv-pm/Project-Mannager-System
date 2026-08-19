package com.example.pmdaily.plan.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.example.pmdaily.plan.CalendarStatus;

/**
 * Working calendar trả về API (docs/api/13-planning-api.md muc 2.4) — PLN-FR-CAL-*.
 */
public record PlanCalendarResponse(
        UUID id,
        String name,
        String description,
        UUID parentCalendarId,
        UUID organizationId,
        Integer dailyWorkingHours,
        String timezone,
        CalendarStatus status,
        long version,
        Instant createdAt,
        List<WorkingDayResponse> workingDays,
        List<PlanCalendarExceptionResponse> exceptions
) {}