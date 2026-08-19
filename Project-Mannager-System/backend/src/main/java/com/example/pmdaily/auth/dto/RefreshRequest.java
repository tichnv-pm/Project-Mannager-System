package com.example.pmdaily.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Cấp token mới (FR-AUTH-02).
 */
public record RefreshRequest(
        @NotBlank(message = "Refresh token không được để trống")
        String refreshToken) {
}
