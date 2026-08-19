package com.example.pmdaily.task.dto;

import java.util.UUID;

/**
 * Thông tin tóm tắt tag trong response.
 */
public record TagBriefResponse(UUID id, String name, String color) {
}
