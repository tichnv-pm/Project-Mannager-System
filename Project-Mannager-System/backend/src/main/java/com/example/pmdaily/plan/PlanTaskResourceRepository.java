package com.example.pmdaily.plan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanTaskResourceRepository extends JpaRepository<PlanTaskResource, UUID> {

    List<PlanTaskResource> findByTaskId(UUID taskId);

    List<PlanTaskResource> findByPlanId(UUID planId);

    List<PlanTaskResource> findByResourceId(UUID resourceId);

    List<PlanTaskResource> findByResourceTypeAndResourceId(ResourceType resourceType, UUID resourceId);

    List<PlanTaskResource> findByResourceType(ResourceType resourceType);

    boolean existsByTaskIdAndResourceTypeAndResourceId(UUID taskId, ResourceType resourceType, UUID resourceId);
}