package com.example.pmdaily.project.dto;

import java.time.Instant;
import java.util.UUID;

import com.example.pmdaily.project.ProjectMemberRole;

/**
 * Response thành viên dự án (docs/api/04-project-api.md muc 3.6).
 */
public record ProjectMemberResponse(
        UUID userId,
        String username,
        String fullName,
        String email,
        ProjectMemberRole role,
        Instant joinedAt) {
}
