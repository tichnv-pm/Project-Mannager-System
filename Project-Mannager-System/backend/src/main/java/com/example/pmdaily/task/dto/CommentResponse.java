package com.example.pmdaily.task.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response bình luận (docs/api/05-task-api.md muc 3.12).
 */
public record CommentResponse(
        UUID id,
        String content,
        UserBriefResponse author,
        Instant createdAt,
        Instant updatedAt) {
}
