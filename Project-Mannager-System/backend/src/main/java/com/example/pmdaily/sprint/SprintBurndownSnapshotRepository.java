package com.example.pmdaily.sprint;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SprintBurndownSnapshotRepository extends JpaRepository<SprintBurndownSnapshot, UUID> {
    List<SprintBurndownSnapshot> findBySprintIdOrderBySnapshotDateAsc(UUID sprintId);
    Optional<SprintBurndownSnapshot> findBySprintIdAndSnapshotDate(UUID sprintId, LocalDate snapshotDate);
}
