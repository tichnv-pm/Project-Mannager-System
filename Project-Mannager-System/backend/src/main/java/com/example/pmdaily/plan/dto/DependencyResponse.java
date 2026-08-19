package com.example.pmdaily.plan.dto;

import java.time.Instant;
import java.util.UUID;

import com.example.pmdaily.plan.DependencyType;

/**
 * Thông tin dependency trả về API (docs/api/13-planning-api.md muc 3.3 — dependencies[]).
 */
public record DependencyResponse(
        UUID id,
        UUID planId,
        UUID predecessorTaskId,
        String predecessorTaskCode,
        UUID successorTaskId,
        String successorTaskCode,
        DependencyType dependencyType,
        int lagMinutes,
        Instant createdAt
) {}