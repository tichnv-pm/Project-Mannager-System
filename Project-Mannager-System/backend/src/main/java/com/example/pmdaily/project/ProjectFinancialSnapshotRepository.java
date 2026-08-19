package com.example.pmdaily.project;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectFinancialSnapshotRepository extends JpaRepository<ProjectFinancialSnapshot, UUID> {
    List<ProjectFinancialSnapshot> findByProjectIdOrderBySnapshotDateAsc(UUID projectId);
    Optional<ProjectFinancialSnapshot> findByProjectIdAndSnapshotDate(UUID projectId, LocalDate snapshotDate);
}
