package com.example.pmdaily.report.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Size;

/**
 * Tạo share-link báo cáo tiến độ cho stakeholder ngoài tổ chức (GĐ4 cải tiến).
 */
public record ReportShareLinkCreateRequest(
        @Size(max = 200, message = "Nhãn tối đa 200 ký tự") String label,
        LocalDate expiresAt) {
}
