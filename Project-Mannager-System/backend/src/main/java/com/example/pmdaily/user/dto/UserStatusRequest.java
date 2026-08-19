package com.example.pmdaily.user.dto;

import com.example.pmdaily.user.UserStatus;

import jakarta.validation.constraints.NotNull;

/**
 * Đổi trạng thái tài khoản (docs/api/02-user-admin-api.md muc 3.5).
 */
public record UserStatusRequest(
        @NotNull(message = "Trạng thái không được để trống")
        UserStatus status,

        @NotNull(message = "Version không được để trống")
        Long version) {
}
