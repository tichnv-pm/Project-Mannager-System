package com.example.pmdaily.plan;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

import com.example.pmdaily.common.BaseAuditEntity;

/**
 * Capacity theo khoảng thời gian của resource (bảng resource_capacities) — docs/planning/10 muc 3.
 */
@Getter
@Setter
@Entity
@Table(name = "resource_capacities")
public class ResourceCapacity extends BaseAuditEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 20)
    private ResourceType resourceType;

    @Column(name = "resource_id", nullable = false)
    private java.util.UUID resourceId;

    @Column(name = "capacity_percent", nullable = false)
    private int capacityPercent = 100;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 10)
    private CapacitySource source = CapacitySource.ORG;
}