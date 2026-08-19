package com.example.pmdaily.plan.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.example.pmdaily.plan.PlanTaskDependency;
import com.example.pmdaily.plan.dto.DependencyResponse;

@Mapper(componentModel = "spring")
public interface PlanDependencyMapper {

    @Mapping(target = "id", source = "d.id")
    @Mapping(target = "planId", source = "d.plan.id")
    @Mapping(target = "predecessorTaskId", source = "d.predecessor.id")
    @Mapping(target = "predecessorTaskCode", source = "d.predecessor.taskCode")
    @Mapping(target = "successorTaskId", source = "d.successor.id")
    @Mapping(target = "successorTaskCode", source = "d.successor.taskCode")
    @Mapping(target = "createdAt", source = "d.createdAt")
    DependencyResponse toResponse(PlanTaskDependency d);
}