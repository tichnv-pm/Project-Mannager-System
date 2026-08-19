package com.example.pmdaily.plan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanChangeRequestRepository extends JpaRepository<PlanChangeRequest, UUID> {

    Optional<PlanChangeRequest> findByIdAndDeletedAtIsNull(UUID id);

    List<PlanChangeRequest> findByPlanIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID planId);
}