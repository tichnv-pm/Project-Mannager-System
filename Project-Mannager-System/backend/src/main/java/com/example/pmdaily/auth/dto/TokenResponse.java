package com.example.pmdaily.auth.dto;

import com.example.pmdaily.user.dto.UserResponse;

/**
 * Cặp token trả khi login/refresh (docs/api/01-auth-api.md muc 3.1).
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserResponse user) {

    public static TokenResponse of(String accessToken, String refreshToken, long expiresIn, UserResponse user) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", expiresIn, user);
    }
}
