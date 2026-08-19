package com.example.pmdaily.qa.dto;

import java.time.Instant;
import java.util.UUID;

public record TestResultResponse(
    UUID id,
    UUID testRunId,
    UUID testCaseId,
    String testCaseTitle,
    String status,
    String actualResult,
    UUID executedBy,
    String executedByName,
    Instant executedAt,
    UUID bugIssueId,
    String bugIssueCode
) {}
