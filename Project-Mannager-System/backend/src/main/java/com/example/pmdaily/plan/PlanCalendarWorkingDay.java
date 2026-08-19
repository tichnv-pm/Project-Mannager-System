package com.example.pmdaily.plan;

import java.time.LocalTime;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Cấu hình ngày làm việc của calendar (bảng plan_calendar_working_days) — PLN-FR-CAL-02.
 * Không version, không soft delete (docs/database/02 muc 30).
 */
@Getter
@Setter
@Entity
@Table(name = "plan_calendar_working_days")
public class PlanCalendarWorkingDay {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calendar_id", nullable = false)
    private PlanCalendar calendar;

    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;

    @Column(name = "is_working", nullable = false)
    private boolean isWorking = true;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;
}