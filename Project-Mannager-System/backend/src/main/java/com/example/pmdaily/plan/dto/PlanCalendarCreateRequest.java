package com.example.pmdaily.plan.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Tạo working calendar (docs/api/13-planning-api.md muc 2.4) — PLN-FR-CAL-01..05.
 * organizationId null = system (tổ chức); parentCalendarId để kế thừa (fallback org).
 */
public record PlanCalendarCreateRequest(

        @NotBlank(message = "name is required")
        @Size(max = 100, message = "name max 100 characters")
        String name,

        String description,

        UUID parentCalendarId,

        UUID organizationId,

        @Min(value = 1, message = "dailyWorkingHours 1-24") @Max(value = 24, message = "dailyWorkingHours 1-24")
        Integer dailyWorkingHours,

        @Size(max = 50, message = "timezone max 50 characters")
        String timezone,

        @Valid
        List<WorkingDayRequest> workingDays
) {

    public int getDailyWorkingHoursOrDefault() {
        return dailyWorkingHours == null ? 8 : dailyWorkingHours;
    }
}