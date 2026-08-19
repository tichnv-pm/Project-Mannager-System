package com.example.pmdaily.project.dto;

import jakarta.validation.constraints.NotNull;

import com.example.pmdaily.project.ProjectMemberRole;

/**
 * Request đổi vai trò thành viên (docs/api/04-project-api.md muc 3.8).
 */
public record ProjectMemberRoleRequest(
        @NotNull(message = "Vai trò không hợp lệ")
        ProjectMemberRole role) {
}
