package com.example.pmdaily.qa.dto;

import java.util.List;
import java.util.UUID;

public record TestCaseResponse(
    UUID id,
    UUID projectId,
    String title,
    String description,
    String preconditions,
    String priority,
    String status,
    List<TestStepDto> steps
) {}
