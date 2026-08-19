package com.example.pmdaily.risk;

import java.time.LocalDate;

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
import com.example.pmdaily.issue.Issue;
import com.example.pmdaily.project.Project;
import com.example.pmdaily.user.User;

/**
 * Rủi ro (bảng risks) — docs/api/08-risk-api.md, FR-RISK-01..05, BR-RISK-01..04.
 */
@Getter
@Setter
@Entity
@Table(name = "risks")
public class Risk extends SoftDeleteEntity {

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "probability", nullable = false, length = 10)
    private RiskProbability probability;

    @Enumerated(EnumType.STRING)
    @Column(name = "impact", nullable = false, length = 10)
    private RiskImpact impact;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false, length = 10)
    private RiskLevel level;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "mitigation_plan")
    private String mitigationPlan;

    @Column(name = "contingency_plan")
    private String contingencyPlan;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RiskStatus status = RiskStatus.OPEN;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_issue_id")
    private Issue linkedIssue;
}
