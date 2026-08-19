package com.example.pmdaily.issue.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.example.pmdaily.issue.IssueSeverity;
import com.example.pmdaily.issue.IssueStatus;
import com.example.pmdaily.task.dto.UserBriefResponse;

public record IssueResponse(
        UUID id,
        String code,
        UUID projectId,
        String title,
        String description,
        IssueSeverity severity,
        UserBriefResponse owner,
        IssueStatus status,
        LocalDate dueDate,
        String rootCause,
        String solution,
        Instant resolvedAt,
        Instant createdAt,
        long version
) {}
