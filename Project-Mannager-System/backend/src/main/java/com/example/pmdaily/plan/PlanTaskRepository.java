package com.example.pmdaily.plan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanTaskRepository extends JpaRepository<PlanTask, UUID> {

    Optional<PlanTask> findByIdAndDeletedAtIsNull(UUID id);

    Optional<PlanTask> findByIdAndPlanIdAndDeletedAtIsNull(UUID id, UUID planId);

    List<PlanTask> findByPlanIdAndDeletedAtIsNull(UUID planId);

    List<PlanTask> findByParentIdAndDeletedAtIsNull(UUID parentId);

    boolean existsByPlanIdAndTaskCodeAndDeletedAtIsNull(UUID planId, String taskCode);

    long countByPlanIdAndDeletedAtIsNull(UUID planId);

    long countByParentIdAndDeletedAtIsNull(UUID parentId);
}
