package com.example.pmdaily.plan;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.example.pmdaily.exception.ResourceNotFoundException;

/**
 * Working calendar hiệu dụng sau khi merge chuỗi kế thừa (org → project override)
 * — dùng chung cho SchedulingEngine (docs/planning/08) và CriticalPath (docs/planning/09).
 * Trừ các ngày không làm việc (weekend theo workingDays + exception NON_WORKING);
 * exception WORKING biến ngày thường thành ngày làm việc.
 */
final class WorkingCalendar {

    static final int DEFAULT_DAILY_MINUTES = 480;

    private final Map<Integer, WorkingDay> week;
    private final Map<LocalDate, CalendarExceptionType> exceptions;
    private final int dailyMinutes;

    private WorkingCalendar(Map<Integer, WorkingDay> week,
            Map<LocalDate, CalendarExceptionType> exceptions, int dailyMinutes) {
        this.week = week;
        this.exceptions = exceptions;
        this.dailyMinutes = dailyMinutes;
    }

    int dailyMinutes() {
        return dailyMinutes;
    }

    boolean isWorking(LocalDate date) {
        CalendarExceptionType exc = exceptions.get(date);
        if (exc != null) {
            return exc == CalendarExceptionType.WORKING;
        }
        WorkingDay wd = week.get(date.getDayOfWeek().getValue());
        return wd != null && wd.isWorking();
    }

    LocalDate nextWorkingDate(LocalDate date) {
        LocalDate cursor = date;
        while (!isWorking(cursor)) {
            cursor = cursor.plusDays(1);
        }
        return cursor;
    }

    /** reference + n ngày làm việc (n >= 0; 0 = ngày làm việc kế tiếp sau reference — FS/FF). */
    LocalDate afterWorkingDays(LocalDate reference, int n) {
        if (n < 0) {
            return minusWorkingDays(reference, -n);
        }
        LocalDate cursor = nextWorkingDate(reference.plusDays(1));
        for (int i = 0; i < n; i++) {
            cursor = nextWorkingDate(cursor.plusDays(1));
        }
        return cursor;
    }

    /** reference + n ngày làm việc, cho phép cùng điểm reference (n >= 0; 0 = chính reference nếu là ngày làm việc). */
    LocalDate inclusivePlusWorkingDays(LocalDate reference, int n) {
        if (n < 0) {
            return minusWorkingDays(reference, -n);
        }
        LocalDate cursor = nextWorkingDate(reference);
        for (int i = 0; i < n; i++) {
            cursor = nextWorkingDate(cursor.plusDays(1));
        }
        return cursor;
    }

    LocalDate minusWorkingDays(LocalDate reference, int n) {
        LocalDate cursor = reference;
        for (int i = 0; i < n; i++) {
            cursor = cursor.minusDays(1);
            while (!isWorking(cursor)) {
                cursor = cursor.minusDays(1);
            }
        }
        return cursor;
    }

    /** Bao gồm ngày bắt đầu: start + (days - 1) ngày làm việc. */
    LocalDate inclusiveEnd(LocalDate start, int days) {
        LocalDate cursor = start;
        for (int i = 1; i < days; i++) {
            cursor = nextWorkingDate(cursor.plusDays(1));
        }
        return cursor;
    }

    /** Số ngày làm việc trong đoạn [start, finish] (tính cả 2 đầu). */
    int workingDaysInSpan(LocalDate start, LocalDate finish) {
        int count = 0;
        LocalDate cursor = start;
        while (!cursor.isAfter(finish)) {
            if (isWorking(cursor)) {
                count++;
            }
            cursor = cursor.plusDays(1);
        }
        return count;
    }

    /** Số ngày làm việc rơi vài (exclusive) giữa 2 ngày: EF -> minES. */
    int workingDaysBetween(LocalDate startInclusive, LocalDate endExclusive) {
        return workingDaysInSpan(startInclusive, endExclusive.minusDays(1));
    }

    static WorkingCalendar build(ProjectPlan plan, PlanCalendarRepository calendarRepository,
            PlanCalendarWorkingDayRepository workingDayRepository,
            PlanCalendarExceptionRepository exceptionRepository) {
        PlanCalendar base = plan.getCalendarId() != null
                ? findCalendar(calendarRepository, plan.getCalendarId())
                : findDefaultCalendar(calendarRepository);

        List<PlanCalendar> chain = new ArrayList<>();
        PlanCalendar cursor = base;
        Set<UUID> seen = new HashSet<>();
        while (cursor != null && !cursor.isDeleted()) {
            chain.add(0, cursor);
            if (!seen.add(cursor.getId()) || cursor.getParentCalendarId() == null) {
                break;
            }
            cursor = findCalendar(calendarRepository, cursor.getParentCalendarId());
        }

        Map<Integer, WorkingDay> week = new LinkedHashMap<>();
        Map<LocalDate, CalendarExceptionType> exceptions = new LinkedHashMap<>();
        Integer dailyMinutes = null;
        for (PlanCalendar c : chain) {
            for (PlanCalendarWorkingDay wd : workingDayRepository.findByCalendarId(c.getId())) {
                week.put(wd.getDayOfWeek(), new WorkingDay(wd.isWorking(), wd.getStartTime(), wd.getEndTime()));
            }
            for (PlanCalendarException e : exceptionRepository.findByCalendarId(c.getId())) {
                exceptions.put(e.getExceptionDate(), e.getExceptionType());
            }
            if (dailyMinutes == null && c.getDailyWorkingHours() != null) {
                dailyMinutes = c.getDailyWorkingHours() * 60;
            }
        }
        return new WorkingCalendar(week, exceptions, dailyMinutes == null ? DEFAULT_DAILY_MINUTES : dailyMinutes);
    }

    private static PlanCalendar findCalendar(PlanCalendarRepository repository, UUID calendarId) {
        return repository.findByIdAndDeletedAtIsNull(calendarId)
                .orElseThrow(() -> new ResourceNotFoundException("Working calendar", calendarId));
    }

    private static PlanCalendar findDefaultCalendar(PlanCalendarRepository repository) {
        return repository.findByDeletedAtIsNull().stream()
                .filter(c -> c.getStatus() == CalendarStatus.ACTIVE)
                .min(Comparator.comparing(PlanCalendar::getCreatedAt))
                .orElseThrow(() -> new ResourceNotFoundException("Working calendar",
                        "Chưa có calendar mặc định (system) cho kế hoạch"));
    }

    private record WorkingDay(boolean isWorking, LocalTime startTime, LocalTime endTime) {
    }
}