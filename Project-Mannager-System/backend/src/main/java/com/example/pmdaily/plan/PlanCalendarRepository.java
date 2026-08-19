package com.example.pmdaily.plan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanCalendarRepository extends JpaRepository<PlanCalendar, UUID> {

    Optional<PlanCalendar> findByIdAndDeletedAtIsNull(UUID id);

    List<PlanCalendar> findByDeletedAtIsNull();

    boolean existsByParentCalendarIdAndDeletedAtIsNull(UUID parentCalendarId);
}