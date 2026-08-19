package com.example.pmdaily.plan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanTaskDependencyRepository extends JpaRepository<PlanTaskDependency, UUID> {

    List<PlanTaskDependency> findByPlan_Id(UUID planId);

    Optional<PlanTaskDependency> findByIdAndPlanId(UUID id, UUID planId);

    boolean existsByPlanIdAndPredecessorIdAndSuccessorIdAndDependencyType(
            UUID planId, UUID predecessorId, UUID successorId, DependencyType dependencyType);

    void deleteByPredecessorId(UUID predecessorId);

    void deleteBySuccessorId(UUID successorId);
}