package com.example.pmdaily.git.dto;

import java.time.Instant;
import java.util.UUID;

public record GitPullRequestResponse(
    UUID id,
    Integer prNumber,
    String title,
    String status,
    String prUrl,
    Instant createdAt,
    Instant updatedAt
) {}
