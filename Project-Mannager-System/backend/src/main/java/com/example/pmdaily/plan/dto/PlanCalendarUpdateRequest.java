package com.example.pmdaily.plan.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.example.pmdaily.plan.CalendarStatus;

/**
 * Cập nhật cấu hình working calendar (docs/api/13-planning-api.md muc 2.4) — PLN-FR-CAL-02.
 * workingDays thay thế toàn bộ danh sách ngày làm việc của calendar (PNL-AC-CAL-02).
 */
public record PlanCalendarUpdateRequest(

        @NotNull(message = "version is required")
        Long version,

        @NotBlank(message = "name is required")
        @Size(max = 100, message = "name max 100 characters")
        String name,

        String description,

        @Min(1) @Max(24)
        Integer dailyWorkingHours,

        @Size(max = 50)
        String timezone,

        @NotNull(message = "status is required")
        CalendarStatus status,

        @Valid
        List<WorkingDayRequest> workingDays
) {

    public int getDailyWorkingHoursOrDefault() {
        return dailyWorkingHours == null ? 8 : dailyWorkingHours;
    }
}