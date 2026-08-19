package com.example.pmdaily.plan;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanCalendarWorkingDayRepository extends JpaRepository<PlanCalendarWorkingDay, UUID> {

    List<PlanCalendarWorkingDay> findByCalendarId(UUID calendarId);

    void deleteByCalendarId(UUID calendarId);
}