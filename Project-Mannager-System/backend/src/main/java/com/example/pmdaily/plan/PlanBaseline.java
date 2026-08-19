package com.example.pmdaily.plan;

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

/**
 * Baseline kế hoạch (bảng plan_baselines) — bất biến, chỉ soft-delete (docs/planning/11 muc 2).
 */
@Getter
@Setter
@Entity
@Table(name = "plan_baselines")
public class PlanBaseline extends SoftDeleteEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private ProjectPlan plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id")
    private PlanVersion planVersion;

    @Column(name = "baseline_num", nullable = false)
    private int baselineNum;

    @Column(name = "description")
    private String description;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt = Instant.now();

    @Column(name = "captured_by")
    private java.util.UUID capturedBy;
}