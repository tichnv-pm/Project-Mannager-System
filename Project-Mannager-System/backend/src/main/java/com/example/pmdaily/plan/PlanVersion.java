package com.example.pmdaily.plan;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

import com.example.pmdaily.common.BaseAuditEntity;

/**
 * Phiên bản kế hoạch (bảng plan_versions) — snapshot tree, versionNo tăng đơn điệu.
 * docs/database/02 muc 26, PLN-FR-VERSION-01..05.
 */
@Getter
@Setter
@Entity
@Table(name = "plan_versions")
public class PlanVersion extends BaseAuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private ProjectPlan plan;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PlanVersionStatus status = PlanVersionStatus.ACTIVE;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_json")
    private String snapshotJson;

    @Column(name = "note")
    private String note;
}
