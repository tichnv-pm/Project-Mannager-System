package com.example.pmdaily.sprint;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Snapshot hàng ngày remaining/completed story points của 1 sprint, dùng vẽ Burndown chart.
 * Ghi bởi {@link SprintBurndownScheduler}; không suy ngược từ audit log (xem V21).
 */
@Getter
@Setter
@Entity
@Table(name = "sprint_burndown_snapshots")
public class SprintBurndownSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "sprint_id", nullable = false)
    private UUID sprintId;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "total_story_points", nullable = false)
    private Integer totalStoryPoints;

    @Column(name = "completed_story_points", nullable = false)
    private Integer completedStoryPoints;

    @Column(name = "remaining_story_points", nullable = false)
    private Integer remainingStoryPoints;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
