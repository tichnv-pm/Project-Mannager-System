package com.example.pmdaily.issue.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.example.pmdaily.issue.IssueSeverity;
import com.example.pmdaily.issue.IssueStatus;

public record IssueUpdateRequest(
        @NotBlank(message = "title is required")
        @Size(max = 200, message = "title max 200 characters")
        String title,

        String description,

        @NotNull(message = "severity is required")
        IssueSeverity severity,

        @NotNull(message = "ownerId is required")
        UUID ownerId,

        String rootCause,

        String solution,

        @NotNull(message = "status is required")
        IssueStatus status,

        LocalDate dueDate,

        @NotNull(message = "version is required")
        Long version
) {}
