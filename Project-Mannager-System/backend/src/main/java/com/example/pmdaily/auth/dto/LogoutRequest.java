package com.example.pmdaily.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Đăng xuất — revoke refresh token (FR-AUTH-03, idempotent).
 */
public record LogoutRequest(
        @NotBlank(message = "Refresh token không được để trống")
        String refreshToken) {
}
