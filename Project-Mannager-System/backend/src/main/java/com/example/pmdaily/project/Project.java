package com.example.pmdaily.project;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

import com.example.pmdaily.common.SoftDeleteEntity;
import com.example.pmdaily.user.User;

/**
 * Dự án (bảng projects) — docs/api/04-project-api.md, BR-PROJ.
 * Soft delete: mọi truy vấn mặc định lọc deleted_at IS NULL (BR-PROJ-07).
 */
@Getter
@Setter
@Entity
@Table(name = "projects")
public class Project extends SoftDeleteEntity {

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProjectStatus status = ProjectStatus.PLANNING;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_manager_id")
    private User projectManager;

    @Column(name = "customer_name", length = 100)
    private String customerName;

    @Column(name = "progress", nullable = false)
    private int progress;

    @Column(name = "note")
    private String note;

    @OneToMany(mappedBy = "project", fetch = FetchType.LAZY)
    private Set<ProjectMember> members = new HashSet<>();
}
