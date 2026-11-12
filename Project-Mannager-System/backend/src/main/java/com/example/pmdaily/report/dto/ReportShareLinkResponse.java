package com.example.pmdaily.report.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Share-link báo cáo tiến độ (GĐ4 cải tiến). `id` cũng chính là token công khai trong URL.
 */
public record ReportShareLinkResponse(
        UUID id,
        UUID projectId,
        String label,
        Instant expiresAt,
        boolean active,
        UUID createdBy,
        Instant createdAt) {
}
