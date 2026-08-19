package com.example.pmdaily.project.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

import com.example.pmdaily.project.ProjectMemberRole;

/**
 * Request thêm thành viên vào dự án (docs/api/04-project-api.md muc 3.7).
 */
public record ProjectMemberRequest(
        @NotNull(message = "Người dùng không tồn tại")
        UUID userId,

        @NotNull(message = "Vai trò không hợp lệ")
        ProjectMemberRole role) {
}
