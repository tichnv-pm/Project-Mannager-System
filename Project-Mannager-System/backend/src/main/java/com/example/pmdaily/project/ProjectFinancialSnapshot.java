package com.example.pmdaily.project;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "project_financial_snapshots")
public class ProjectFinancialSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "planned_value", nullable = false)
    private Double plannedValue;

    @Column(name = "earned_value", nullable = false)
    private Double earnedValue;

    @Column(name = "actual_cost", nullable = false)
    private Double actualCost;

    @Column(name = "cost_variance", nullable = false)
    private Double costVariance;

    @Column(name = "schedule_variance", nullable = false)
    private Double scheduleVariance;

    @Column(name = "cpi", nullable = false)
    private Double cpi;

    @Column(name = "spi", nullable = false)
    private Double spi;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
