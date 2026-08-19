package com.example.pmdaily.task.dto;

import java.util.UUID;

/**
 * Thông tin tóm tắt người dùng trong response (docs/api/05-task-api.md muc 3.3).
 */
public record UserBriefResponse(UUID id, String fullName) {
}
