package com.example.pmdaily.task.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request cập nhật tiến độ (docs/api/05-task-api.md muc 3.9).
 */
public record ProgressUpdateRequest(
        @NotNull(message = "Tiến độ không được để trống")
        @Min(value = 0, message = "Tiến độ tối thiểu 0")
        @Max(value = 100, message = "Tiến độ tối đa 100")
        Integer progress) {
}
