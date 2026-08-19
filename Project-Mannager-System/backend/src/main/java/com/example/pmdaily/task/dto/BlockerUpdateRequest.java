package com.example.pmdaily.task.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request đánh dấu/gỡ blocker (docs/api/05-task-api.md muc 3.10, FR-TASK-09).
 */
public record BlockerUpdateRequest(
        @NotNull(message = "Trạng thái blocker không được để trống")
        Boolean blocked,

        @Size(max = 500, message = "Lý do blocker tối đa 500 ký tự")
        String blockerReason) {
}
