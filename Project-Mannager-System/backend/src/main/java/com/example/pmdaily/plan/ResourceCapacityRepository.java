package com.example.pmdaily.plan;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceCapacityRepository extends JpaRepository<ResourceCapacity, UUID> {

    List<ResourceCapacity> findByResourceTypeAndResourceIdOrderByStartDate(
            ResourceType resourceType, UUID resourceId);

    java.util.Optional<ResourceCapacity> findByResourceTypeAndResourceIdAndStartDate(
            ResourceType resourceType, UUID resourceId, LocalDate startDate);
}