package com.example.pmdaily.wiki.dto;

import java.time.Instant;
import java.util.UUID;

public record WikiPageResponse(
        UUID id,
        UUID projectId,
        UUID parentPageId,
        String title,
        String content,
        long version,
        Instant createdAt,
        UUID createdBy,
        Instant updatedAt,
        UUID updatedBy
) {}
