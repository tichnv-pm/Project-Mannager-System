package com.example.pmdaily.plan.mapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.example.pmdaily.plan.CalendarExceptionType;
import com.example.pmdaily.plan.CalendarStatus;
import com.example.pmdaily.plan.PlanCalendar;
import com.example.pmdaily.plan.PlanCalendarException;
import com.example.pmdaily.plan.PlanCalendarWorkingDay;
import com.example.pmdaily.plan.dto.PlanCalendarExceptionResponse;
import com.example.pmdaily.plan.dto.PlanCalendarResponse;
import com.example.pmdaily.plan.dto.WorkingDayRequest;
import com.example.pmdaily.plan.dto.WorkingDayResponse;

@Mapper(componentModel = "spring")
public interface PlanCalendarMapper {

    @Mapping(target = "id", source = "c.id")
    @Mapping(target = "name", source = "c.name")
    @Mapping(target = "description", source = "c.description")
    @Mapping(target = "parentCalendarId", source = "c.parentCalendarId")
    @Mapping(target = "organizationId", source = "c.organizationId")
    @Mapping(target = "dailyWorkingHours", source = "c.dailyWorkingHours")
    @Mapping(target = "timezone", source = "c.timezone")
    @Mapping(target = "status", source = "c.status")
    @Mapping(target = "version", source = "c.version")
    @Mapping(target = "createdAt", source = "c.createdAt")
    @Mapping(target = "workingDays", source = "workingDays")
    @Mapping(target = "exceptions", source = "exceptions")
    PlanCalendarResponse toResponse(PlanCalendar c, List<WorkingDayResponse> workingDays,
            List<PlanCalendarExceptionResponse> exceptions);

    @Mapping(target = "id", source = "wd.id")
    @Mapping(target = "dayOfWeek", source = "wd.dayOfWeek")
    @Mapping(target = "isWorking", source = "wd.working")
    @Mapping(target = "startTime", source = "wd.startTime")
    @Mapping(target = "endTime", source = "wd.endTime")
    WorkingDayResponse toWorkingDayResponse(PlanCalendarWorkingDay wd);

    @Mapping(target = "id", source = "e.id")
    @Mapping(target = "exceptionDate", source = "e.exceptionDate")
    @Mapping(target = "exceptionType", source = "e.exceptionType")
    @Mapping(target = "note", source = "e.note")
    PlanCalendarExceptionResponse toExceptionResponse(PlanCalendarException e);

    default List<WorkingDayResponse> toWorkingDayResponses(List<PlanCalendarWorkingDay> days) {
        if (days == null) {
            return List.of();
        }
        return days.stream().map(this::toWorkingDayResponse).collect(Collectors.toList());
    }

    default List<PlanCalendarExceptionResponse> toExceptionResponses(List<PlanCalendarException> exceptions) {
        if (exceptions == null) {
            return List.of();
        }
        return exceptions.stream().map(this::toExceptionResponse).collect(Collectors.toList());
    }

    default PlanCalendarWorkingDay toWorkingDayEntity(PlanCalendar calendar, WorkingDayRequest req) {
        PlanCalendarWorkingDay wd = new PlanCalendarWorkingDay();
        wd.setCalendar(calendar);
        wd.setDayOfWeek(req.dayOfWeek());
        wd.setWorking(req.working());
        wd.setStartTime(req.startTime());
        wd.setEndTime(req.endTime());
        return wd;
    }
}