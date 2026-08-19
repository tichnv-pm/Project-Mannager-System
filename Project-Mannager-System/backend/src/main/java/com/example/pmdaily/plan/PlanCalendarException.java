package com.example.pmdaily.plan;

import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Exception (holiday / working bù) của calendar (bảng plan_calendar_exceptions) — PLN-FR-CAL-03/04.
 * Không version, không soft delete (docs/database/02 muc 31).
 */
@Getter
@Setter
@Entity
@Table(name = "plan_calendar_exceptions")
public class PlanCalendarException {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calendar_id", nullable = false)
    private PlanCalendar calendar;

    @Column(name = "exception_date", nullable = false)
    private LocalDate exceptionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "exception_type", nullable = false, length = 20)
    private CalendarExceptionType exceptionType;

    @Column(name = "note", length = 200)
    private String note;
}