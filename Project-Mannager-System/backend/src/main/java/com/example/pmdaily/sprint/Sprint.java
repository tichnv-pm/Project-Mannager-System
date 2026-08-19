package com.example.pmdaily.sprint;

import java.time.LocalDate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import com.example.pmdaily.common.SoftDeleteEntity;

@Entity
@Table(name = "sprints")
@Getter
@Setter
@NoArgsConstructor
public class Sprint extends SoftDeleteEntity {

    @Column(name = "project_id", nullable = false)
    private java.util.UUID projectId;

    @Column(name = "sprint_name", nullable = false, length = 255)
    private String sprintName;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private SprintStatus status = SprintStatus.FUTURE;

    @Column(name = "goal")
    private String goal;
}
