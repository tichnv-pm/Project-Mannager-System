package com.example.pmdaily.plan;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.pmdaily.plan.dto.GanttDataResponse;
import com.example.pmdaily.security.UserPrincipal;

/**
 * Gantt data read-only (docs/api/13-planning-api.md muc 3.3, docs/planning/13 muc 5) — PLN-FE-10.
 */
@RestController
@RequestMapping("/api/v1")
public class PlanGanttController {

    private final PlanGanttService ganttService;

    public PlanGanttController(PlanGanttService ganttService) {
        this.ganttService = ganttService;
    }

    @GetMapping("/plans/{planId}/gantt")
    @PreAuthorize("hasAuthority('plan:view')")
    public GanttDataResponse gantt(@AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID planId) {
        return ganttService.build(actor, planId);
    }
}