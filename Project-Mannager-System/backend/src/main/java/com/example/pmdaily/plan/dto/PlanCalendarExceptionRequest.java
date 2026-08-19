package com.example.pmdaily.plan.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.example.pmdaily.plan.CalendarExceptionType;

/**
 * Thêm exception (holiday / working bù) cho working calendar (docs/database/02 muc 31,
 * docs/api/13-planning-api.md muc 2.4) — PLN-FR-CAL-03/04. UNIQUE (calendar_id, exception_date).
 */
public record PlanCalendarExceptionRequest(

        @NotNull(message = "exceptionDate is required")
        LocalDate exceptionDate,

        @NotNull(message = "exceptionType is required")
        CalendarExceptionType exceptionType,

        @Size(max = 200, message = "note max 200 characters")
        String note
) {}