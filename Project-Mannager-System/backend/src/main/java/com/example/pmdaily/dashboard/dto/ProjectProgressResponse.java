package com.example.pmdaily.dashboard.dto;

import java.util.List;
import java.util.UUID;

public record ProjectProgressResponse(
        List<ProjectProgressItem> projects
) {
    public record ProjectProgressItem(
            UUID projectId,
            String code,
            String name,
            int progress
    ) {}
}
