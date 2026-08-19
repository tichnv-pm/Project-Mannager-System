package com.example.pmdaily.plan;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanTemplateTaskRepository extends JpaRepository<PlanTemplateTask, UUID> {
    List<PlanTemplateTask> findByTemplateIdOrderBySequenceNoAsc(UUID templateId);
}
