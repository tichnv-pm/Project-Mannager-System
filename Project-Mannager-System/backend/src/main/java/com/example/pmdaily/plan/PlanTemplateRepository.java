package com.example.pmdaily.plan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanTemplateRepository extends JpaRepository<PlanTemplate, UUID> {
    Optional<PlanTemplate> findByTemplateCode(String templateCode);
    List<PlanTemplate> findByStatus(TemplateStatus status);
}
