package com.example.pmdaily.report.dto;

import java.time.Instant;

/**
 * Báo cáo tiến độ dự án xem qua share-link công khai (GĐ4 cải tiến) — chỉ các field tổng hợp,
 * không lộ bất kỳ thông tin nội bộ nào ngoài những gì PM chủ động chia sẻ.
 */
public record PublicProjectProgressResponse(
        String projectCode,
        String projectName,
        String label,
        int progress,
        long totalTasks,
        long doneTasks,
        Instant generatedAt) {
}
