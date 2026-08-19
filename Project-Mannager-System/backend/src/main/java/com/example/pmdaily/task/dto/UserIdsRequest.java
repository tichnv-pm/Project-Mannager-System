package com.example.pmdaily.task.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * Request thay thế toàn bộ danh sách user (collaborators/watchers) — docs/api/05 muc 3.11.
 */
public record UserIdsRequest(
        @NotNull(message = "Danh sách người dùng không được để trống")
        List<UUID> userIds) {
}
