package com.example.pmdaily.plan;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.example.pmdaily.common.SoftDeleteEntity;

/**
 * Change suggestion (plan_change_requests) — docs/planning/02 muc 2.10, docs/api/13-planning-api.md muc 2.9.
 * Duyệt đơn khi tổng effort < 10.000 phút; dual approve (PM + ADMIN) khi >= 10.000 (PLN-AC-CHG-02b).
 */
@Getter
@Setter
@Entity
@Table(name = "plan_change_requests")
public class PlanChangeRequest extends SoftDeleteEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private ProjectPlan plan;

    @Column(name = "source_type", length = 30)
    private String sourceType;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", nullable = false)
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "suggested_changes", nullable = false)
    private String suggestedChanges;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PlanChangeRequestStatus status = PlanChangeRequestStatus.PENDING;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by_2")
    private UUID reviewedBy2;

    @Column(name = "reviewed_at_2")
    private Instant reviewedAt2;
}