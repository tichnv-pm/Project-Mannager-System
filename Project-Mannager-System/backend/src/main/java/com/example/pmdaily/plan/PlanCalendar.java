package com.example.pmdaily.plan;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

import com.example.pmdaily.common.SoftDeleteEntity;

/**
 * Working calendar (bảng plan_calendars) — PLN-FR-CAL-*.
 * docs/database/02 muc 29, docs/planning/08 muc 4.
 */
@Getter
@Setter
@Entity
@Table(name = "plan_calendars")
public class PlanCalendar extends SoftDeleteEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "parent_calendar_id")
    private UUID parentCalendarId;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "daily_working_hours")
    private Integer dailyWorkingHours;

    @Column(name = "timezone", length = 50)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CalendarStatus status = CalendarStatus.ACTIVE;
}