package com.example.pmdaily.project.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.example.pmdaily.project.ProjectStatus;

/**
 * Request cập nhật dự án — kèm version (optimistic locking, BR-GEN-08).
 */
public record ProjectUpdateRequest(
        @NotBlank(message = "Mã dự án không được để trống")
        @Size(min = 3, max = 20, message = "Mã dự án phải dài 3–20 ký tự")
        String code,

        @NotBlank(message = "Tên dự án không được để trống")
        @Size(max = 100, message = "Tên dự án tối đa 100 ký tự")
        String name,

        @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự")
        String description,

        ProjectStatus status,

        LocalDate startDate,

        LocalDate endDate,

        @Size(max = 100, message = "Tên khách hàng tối đa 100 ký tự")
        String customerName,

        UUID projectManagerId,

        String note,

        @NotNull(message = "Phiên bản không hợp lệ")
        Long version) {
}
