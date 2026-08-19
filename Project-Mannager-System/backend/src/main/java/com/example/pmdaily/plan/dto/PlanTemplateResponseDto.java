package com.example.pmdaily.plan.dto;

import java.util.UUID;

import com.example.pmdaily.plan.TemplateStatus;
import com.example.pmdaily.plan.TemplateType;

import lombok.Data;

@Data
public class PlanTemplateResponseDto {
    private UUID id;
    private String templateCode;
    private String templateName;
    private String description;
    private TemplateType templateType;
    private String category;
    private Integer versionNo;
    private TemplateStatus status;
    private Boolean isBuiltIn;
    private int taskCount;
}
