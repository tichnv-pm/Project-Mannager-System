package com.example.pmdaily.plan;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.pmdaily.plan.dto.CreatePlanFromTemplateRequestDto;
import com.example.pmdaily.plan.dto.PlanResponse;
import com.example.pmdaily.plan.dto.PlanTemplateDetailResponseDto;
import com.example.pmdaily.plan.dto.PlanTemplateResponseDto;
import com.example.pmdaily.security.UserPrincipal;

import jakarta.validation.Valid;

@RestController
public class PlanTemplateController {

    private final PlanTemplateService templateService;

    public PlanTemplateController(PlanTemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping("/api/v1/plan-templates")
    @PreAuthorize("hasAuthority('plan:view')")
    public ResponseEntity<List<PlanTemplateResponseDto>> getAllTemplates(
            @RequestParam(required = false) TemplateStatus status) {
        return ResponseEntity.ok(templateService.getAllTemplates(status));
    }

    @GetMapping("/api/v1/plan-templates/{id}")
    @PreAuthorize("hasAuthority('plan:view')")
    public ResponseEntity<PlanTemplateDetailResponseDto> getTemplateDetail(
            @PathVariable UUID id) {
        return ResponseEntity.ok(templateService.getTemplateDetail(id));
    }

    @PostMapping("/api/v1/plans/from-template")
    @PreAuthorize("hasAuthority('plan:create')")
    public ResponseEntity<PlanResponse> createPlanFromTemplate(
            @Valid @RequestBody CreatePlanFromTemplateRequestDto request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        PlanResponse createdPlan = templateService.createPlanFromTemplate(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdPlan);
    }
}
