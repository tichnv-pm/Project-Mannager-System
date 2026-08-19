package com.example.pmdaily.risk.dto;

import java.time.LocalDate;

import com.example.pmdaily.issue.IssueSeverity;

public record RiskConvertToIssueRequest(
        IssueSeverity severity,
        LocalDate dueDate
) {}
