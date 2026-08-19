package com.example.pmdaily.qa.dto;

import jakarta.validation.constraints.NotBlank;

public record TestResultUpdateRequest(
    @NotBlank(message = "Kết quả kiểm thử không được để trống")
    String status, // PASSED, FAILED, BLOCKED

    String actualResult
) {}
