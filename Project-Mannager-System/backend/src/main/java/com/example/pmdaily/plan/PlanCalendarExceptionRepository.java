package com.example.pmdaily.plan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanCalendarExceptionRepository extends JpaRepository<PlanCalendarException, UUID> {

    List<PlanCalendarException> findByCalendarId(UUID calendarId);

    Optional<PlanCalendarException> findByCalendarIdAndExceptionDate(UUID calendarId, java.time.LocalDate exceptionDate);

    void deleteByCalendarId(UUID calendarId);
}