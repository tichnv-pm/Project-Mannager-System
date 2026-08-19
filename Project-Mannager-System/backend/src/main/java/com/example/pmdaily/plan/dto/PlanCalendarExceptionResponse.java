package com.example.pmdaily.plan.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.example.pmdaily.plan.CalendarExceptionType;

/**
 * Exception của working calendar trả về API (docs/database/02 muc 31).
 */
public record PlanCalendarExceptionResponse(
        UUID id,
        LocalDate exceptionDate,
        CalendarExceptionType exceptionType,
        String note
) {}