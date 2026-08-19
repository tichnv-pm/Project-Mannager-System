package com.example.pmdaily.plan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanLinkRepository extends JpaRepository<PlanLink, UUID> {

    Optional<PlanLink> findByIdAndDeletedAtIsNull(UUID id);

    List<PlanLink> findByPlanningTaskIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID planningTaskId);

    boolean existsByPlanningTaskIdAndTargetTypeAndTargetIdAndDeletedAtIsNull(
            UUID planningTaskId, PlanLinkTargetType targetType, UUID targetId);

    Optional<PlanLink> findByPlanningTaskIdAndIsPrimaryExecutionTrueAndDeletedAtIsNull(UUID planningTaskId);

    Optional<PlanLink> findByTargetTypeAndTargetIdAndIsPrimaryExecutionTrueAndDeletedAtIsNull(
            PlanLinkTargetType targetType, UUID targetId);
}