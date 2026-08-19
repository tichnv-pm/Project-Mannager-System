package com.example.pmdaily.plan;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanBaselineTaskRepository extends JpaRepository<PlanBaselineTask, UUID> {

    List<PlanBaselineTask> findByBaselineId(UUID baselineId);
}