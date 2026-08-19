package com.example.pmdaily.plan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanBaselineRepository extends JpaRepository<PlanBaseline, UUID> {

    List<PlanBaseline> findByPlanIdAndDeletedAtIsNullOrderByBaselineNumDesc(UUID planId);

    Optional<PlanBaseline> findFirstByPlanIdAndDeletedAtIsNullOrderByBaselineNumDesc(UUID planId);

    Optional<PlanBaseline> findByPlanIdAndBaselineNumAndDeletedAtIsNull(UUID planId, int baselineNum);

    List<PlanBaseline> findByPlanIdOrderByBaselineNumDesc(UUID planId);

    boolean existsByPlanIdAndBaselineNum(UUID planId, int baselineNum);
}