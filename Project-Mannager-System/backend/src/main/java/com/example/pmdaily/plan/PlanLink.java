package com.example.pmdaily.plan;

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

import com.example.pmdaily.common.SoftDeleteEntity;

/**
 * Liên kết planning task với entity ngoài (bảng plan_links) — docs/planning/02 muc 2.11,
 * docs/api/13-planning-api.md muc 2.8. Bảng riêng, không cột danh sách (PLN-RULE-LINK-01).
 */
@Getter
@Setter
@Entity
@Table(name = "plan_links")
public class PlanLink extends SoftDeleteEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private ProjectPlan plan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "planning_task_id", nullable = false)
    private PlanTask planningTask;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30)
    private PlanLinkTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", nullable = false, length = 20)
    private PlanLinkType linkType = PlanLinkType.RELATED;

    @Column(name = "note")
    private String note;

    @Column(name = "is_primary_execution", nullable = false)
    private boolean isPrimaryExecution = false;
}