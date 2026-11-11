package com.example.pmdaily.dod.dto;

import java.util.UUID;

public record ProjectDodItemResponse(
        UUID id,
        UUID projectId,
        String text,
        int displayOrder,
        boolean active,
        long version) {
}
