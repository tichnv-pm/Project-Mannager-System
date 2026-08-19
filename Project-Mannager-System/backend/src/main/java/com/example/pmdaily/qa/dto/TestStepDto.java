package com.example.pmdaily.qa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TestStepDto(
    @NotNull(message = "Số thứ tự bước không được để trống")
    Integer stepNumber,

    @NotBlank(message = "Hành động bước không được để trống")
    String action,

    @NotBlank(message = "Kết quả kỳ vọng không được để trống")
    String expectedResult
) {}
