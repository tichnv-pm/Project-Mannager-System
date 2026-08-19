package com.example.pmdaily.qa.dto;

import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record TestRunCreateRequest(
    @NotBlank(message = "Tên đợt kiểm thử không được để trống")
    @Size(max = 255, message = "Tên đợt kiểm thử tối đa 255 ký tự")
    String name,

    String description,

    @NotEmpty(message = "Danh sách kịch bản test không được trống")
    List<UUID> testCaseIds
) {}
