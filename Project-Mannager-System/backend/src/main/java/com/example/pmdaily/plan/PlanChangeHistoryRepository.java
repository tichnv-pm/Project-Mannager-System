package com.example.pmdaily.plan;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanChangeHistoryRepository extends JpaRepository<PlanChangeHistory, UUID> {

    List<PlanChangeHistory> findByPlanIdAndDeletedAtIsNullOrderByChangedAtDesc(UUID planId);

    long countByPlanIdAndDeletedAtIsNull(UUID planId);
}