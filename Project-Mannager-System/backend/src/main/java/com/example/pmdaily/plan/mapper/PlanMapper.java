package com.example.pmdaily.plan.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.example.pmdaily.plan.ProjectPlan;
import com.example.pmdaily.plan.dto.PlanResponse;

@Mapper(componentModel = "spring")
public interface PlanMapper {

    @Mapping(target = "id", source = "plan.id")
    @Mapping(target = "projectId", source = "plan.project.id")
    @Mapping(target = "parentPlanId", source = "plan.parentPlan.id")
    @Mapping(target = "calendarId", source = "plan.calendarId")
    @Mapping(target = "activeVersionId", source = "plan.activeVersion.id")
    @Mapping(target = "activeVersionNo", expression = "java(plan.getActiveVersion() != null ? plan.getActiveVersion().getVersionNo() : null)")
    @Mapping(target = "createdAt", source = "plan.createdAt")
    PlanResponse toResponse(ProjectPlan plan);
}
