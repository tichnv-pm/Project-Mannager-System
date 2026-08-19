package com.example.pmdaily.qa.dto;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TestCaseUpdateRequest(
    @NotBlank(message = "Tiêu đề test case không được để trống")
    @Size(max = 255, message = "Tiêu đề test case tối đa 255 ký tự")
    String title,

    String description,
    String preconditions,
    String priority,
    String status,

    @Valid
    List<TestStepDto> steps
) {}
