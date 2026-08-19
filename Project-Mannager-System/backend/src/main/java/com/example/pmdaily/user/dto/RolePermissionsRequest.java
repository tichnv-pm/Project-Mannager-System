package com.example.pmdaily.user.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;

/**
 * Gán quyền cho vai trò (docs/api/02-user-admin-api.md muc 3.7) — thay thế toàn bộ.
 */
public record RolePermissionsRequest(
        @NotEmpty(message = "Phải chọn ít nhất một quyền")
        List<UUID> permissionIds) {
}
