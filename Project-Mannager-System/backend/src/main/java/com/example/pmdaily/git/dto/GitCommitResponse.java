package com.example.pmdaily.git.dto;

import java.time.Instant;
import java.util.UUID;

public record GitCommitResponse(
    UUID id,
    String commitHash,
    String message,
    String author,
    String commitUrl,
    Instant createdAt
) {}
