package com.example.pmdaily.plan.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.example.pmdaily.plan.PlanTemplate;
import com.example.pmdaily.plan.PlanTemplateTask;
import com.example.pmdaily.plan.dto.PlanTemplateDetailResponseDto;
import com.example.pmdaily.plan.dto.PlanTemplateResponseDto;
import com.example.pmdaily.plan.dto.PlanTemplateTaskResponseDto;

@Mapper(componentModel = "spring")
public interface PlanTemplateMapper {

    @Mapping(target = "taskCount", expression = "java(template.getTasks() != null ? template.getTasks().size() : 0)")
    PlanTemplateResponseDto toResponseDto(PlanTemplate template);

    PlanTemplateDetailResponseDto toDetailResponseDto(PlanTemplate template);

    PlanTemplateTaskResponseDto toTaskResponseDto(PlanTemplateTask task);
}
