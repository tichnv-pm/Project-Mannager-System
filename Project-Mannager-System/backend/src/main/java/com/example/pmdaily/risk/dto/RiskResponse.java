package com.example.pmdaily.risk.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.example.pmdaily.risk.RiskImpact;
import com.example.pmdaily.risk.RiskLevel;
import com.example.pmdaily.risk.RiskProbability;
import com.example.pmdaily.risk.RiskStatus;
import com.example.pmdaily.task.dto.UserBriefResponse;

public record RiskResponse(
        UUID id,
        String code,
        UUID projectId,
        String title,
        String description,
        RiskProbability probability,
        RiskImpact impact,
        RiskLevel level,
        UserBriefResponse owner,
        String mitigationPlan,
        String contingencyPlan,
        RiskStatus status,
        LocalDate dueDate,
        UUID linkedIssueId,
        Instant createdAt,
        long version
) {}
