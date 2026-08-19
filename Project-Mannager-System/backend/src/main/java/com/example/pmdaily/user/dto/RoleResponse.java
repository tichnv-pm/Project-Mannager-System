package com.example.pmdaily.user.dto;

import java.util.List;
import java.util.UUID;

/**
 * Vai trò hệ thống + quyền (docs/api/02-user-admin-api.md muc 3.6).
 */
public record RoleResponse(
        UUID id,
        String code,
        String name,
        String description,
        boolean isSystem,
        List<String> permissions) {
}
