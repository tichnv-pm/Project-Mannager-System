package com.example.pmdaily.plan;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProjectPlanRepository extends JpaRepository<ProjectPlan, UUID>,
        JpaSpecificationExecutor<ProjectPlan> {

    boolean existsByProjectIdAndPlanCodeAndDeletedAtIsNull(UUID projectId, String planCode);

    Optional<ProjectPlan> findByIdAndDeletedAtIsNull(UUID id);

    List<ProjectPlan> findByParentPlanIdAndDeletedAtIsNull(UUID parentPlanId);

    List<ProjectPlan> findByProjectIdAndDeletedAtIsNull(UUID projectId);

    long countByProjectIdAndPlanTypeAndStatusInAndDeletedAtIsNull(
            UUID projectId, PlanType planType, Collection<PlanStatus> statuses);

    long countByProjectIdAndPlanTypeAndStatusInAndDeletedAtIsNullAndIdNot(
            UUID projectId, PlanType planType, Collection<PlanStatus> statuses, UUID excludeId);

    long countByCalendarIdAndDeletedAtIsNull(UUID calendarId);
}