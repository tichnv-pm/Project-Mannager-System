package com.example.pmdaily.plan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanVersionRepository extends JpaRepository<PlanVersion, UUID> {

    Optional<PlanVersion> findFirstByPlanIdOrderByVersionNoDesc(UUID planId);

    List<PlanVersion> findByPlanIdOrderByVersionNoDesc(UUID planId);

    boolean existsByPlanIdAndVersionNo(UUID planId, int versionNo);
}