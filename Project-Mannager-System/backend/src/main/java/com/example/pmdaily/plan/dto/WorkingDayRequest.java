package com.example.pmdaily.plan.dto;

import java.time.LocalTime;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Một dòng cấu hình ngày trong tuần của working calendar (docs/database/02 muc 30) — PLN-FR-CAL-02.
 * dayOfWeek: 1 = Thứ 2 .. 7 = Chủ nhật.
 */
public record WorkingDayRequest(
        @NotNull(message = "dayOfWeek is required")
        @Min(value = 1, message = "dayOfWeek 1-7") @Max(value = 7, message = "dayOfWeek 1-7")
        Integer dayOfWeek,
        Boolean isWorking,
        @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.TIME)
        LocalTime startTime,
        @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.TIME)
        LocalTime endTime
) {
    public boolean working() {
        return isWorking == null || isWorking;
    }
}