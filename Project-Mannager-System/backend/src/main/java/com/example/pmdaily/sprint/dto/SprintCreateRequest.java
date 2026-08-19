package com.example.pmdaily.sprint.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SprintCreateRequest(
    @NotBlank(message = "Tên Sprint không được để trống")
    @Size(max = 255, message = "Tên Sprint tối đa 255 ký tự")
    String sprintName,

    @NotNull(message = "Ngày bắt đầu không được để trống")
    LocalDate startDate,

    @NotNull(message = "Ngày kết thúc không được để trống")
    LocalDate endDate,

    String goal
) {}
