package com.example.pmdaily.user.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.example.pmdaily.user.UserStatus;

/**
 * Thông tin user trả qua API — không bao giờ chứa passwordHash (BR-AUTH-03).
 * Docs/api/02-user-admin-api.md muc 3.1: kèm status + createdAt; version dùng cho optimistic locking.
 */
public record UserResponse(
        UUID id,
        String username,
        String fullName,
        String email,
        UserStatus status,
        Instant createdAt,
        long version,
        List<String> roles,
        List<String> permissions) {
}
