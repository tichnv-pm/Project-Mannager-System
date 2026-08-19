package com.example.pmdaily.plan.dto;

import java.time.LocalTime;
import java.util.UUID;

/**
 * Cấu hình 1 ngày trong tuần trả về API.
 */
public record WorkingDayResponse(
        UUID id,
        int dayOfWeek,
        boolean isWorking,
        LocalTime startTime,
        LocalTime endTime
) {}