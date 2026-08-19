package com.example.pmdaily.milestone.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.example.pmdaily.milestone.Milestone;
import com.example.pmdaily.milestone.dto.MilestoneResponse;

@Mapper(componentModel = "spring")
public interface MilestoneMapper {

    @Mapping(target = "id", source = "milestone.id")
    @Mapping(target = "projectId", source = "milestone.project.id")
    @Mapping(target = "projectCode", source = "milestone.project.code")
    @Mapping(target = "projectName", source = "milestone.project.name")
    @Mapping(target = "createdAt", source = "milestone.createdAt")
    MilestoneResponse toResponse(Milestone milestone);
}
