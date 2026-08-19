package com.example.pmdaily.qa.dto;

import java.time.Instant;
import java.util.UUID;

public record TestRunResponse(
    UUID id,
    UUID projectId,
    String name,
    String description,
    String status,
    Instant createdAt,
    UUID createdBy
) {}
