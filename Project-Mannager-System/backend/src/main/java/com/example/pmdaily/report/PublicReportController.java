package com.example.pmdaily.report;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.pmdaily.report.dto.PublicProjectProgressResponse;

/**
 * Xem báo cáo tiến độ qua share-link, không cần đăng nhập (GĐ4 cải tiến, stakeholder ngoài tổ
 * chức). `permitAll()` trong SecurityConfig, cùng mẫu `/api/v1/public/webhooks/**` đã có — token
 * là chính id (UUID) của share-link, không đoán được.
 */
@RestController
@RequestMapping("/api/v1/public/shared-reports")
public class PublicReportController {

    private final ReportShareLinkService shareLinkService;

    public PublicReportController(ReportShareLinkService shareLinkService) {
        this.shareLinkService = shareLinkService;
    }

    @GetMapping("/{token}")
    public PublicProjectProgressResponse view(@PathVariable UUID token) {
        return shareLinkService.getPublicProjectProgress(token);
    }
}
