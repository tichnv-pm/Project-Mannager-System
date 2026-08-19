package com.example.pmdaily.plan.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.example.pmdaily.plan.PlanTask;
import com.example.pmdaily.plan.dto.PlanTaskResponse;

@Mapper(componentModel = "spring")
public interface PlanTaskMapper {

    @Mapping(target = "id", source = "task.id")
    @Mapping(target = "planId", source = "task.plan.id")
    @Mapping(target = "parentId", source = "task.parent.id")
    @Mapping(target = "ownerId", source = "task.owner.id")
    @Mapping(target = "createdAt", source = "task.createdAt")
    @Mapping(target = "isSummary", source = "task.summary")
    @Mapping(target = "isMilestone", source = "task.milestone")
    @Mapping(target = "isCritical", source = "task.critical")
    PlanTaskResponse toResponse(PlanTask task);
}
