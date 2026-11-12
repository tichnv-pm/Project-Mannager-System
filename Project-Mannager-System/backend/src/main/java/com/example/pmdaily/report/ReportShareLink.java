package com.example.pmdaily.report;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

import com.example.pmdaily.common.SoftDeleteEntity;
import com.example.pmdaily.project.Project;

/**
 * Share-link cho stakeholder ngoài tổ chức xem báo cáo tiến độ dự án không cần đăng nhập
 * (GĐ4 cải tiến, docs/api/12-report-audit-api.md). Token công khai = chính id (UUID) của bản ghi,
 * cùng quy ước "UUID PK không đoán được" đã dùng cho mọi entity khác. Revoke = soft delete
 * (deletedAt) — không cần field riêng.
 */
@Getter
@Setter
@Entity
@Table(name = "report_share_links")
public class ReportShareLink extends SoftDeleteEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "label", length = 200)
    private String label;

    @Column(name = "expires_at")
    private Instant expiresAt;

    public boolean isActive() {
        return !isDeleted() && (expiresAt == null || expiresAt.isAfter(Instant.now()));
    }
}
