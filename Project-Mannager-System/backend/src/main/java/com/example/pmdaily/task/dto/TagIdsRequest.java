package com.example.pmdaily.task.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * Request thay thế toàn bộ tag của công việc — docs/api/05 muc 3.11.
 */
public record TagIdsRequest(
        @NotNull(message = "Danh sách tag không được để trống")
        List<UUID> tagIds) {
}
