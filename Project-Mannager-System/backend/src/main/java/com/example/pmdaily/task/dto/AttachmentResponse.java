package com.example.pmdaily.task.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response file đính kèm (docs/api/05-task-api.md muc 3.13).
 * filePath là URL tải file.
 */
public record AttachmentResponse(
        UUID id,
        String fileName,
        String filePath,
        String contentType,
        long sizeBytes,
        UserBriefResponse uploadedBy,
        Instant createdAt) {
}
