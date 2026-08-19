package com.example.pmdaily.project;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {

    @EntityGraph(attributePaths = {"user"})
    List<ProjectMember> findByProjectIdOrderByCreatedAtAsc(UUID projectId);

    Optional<ProjectMember> findByProjectIdAndUser_Id(UUID projectId, UUID userId);

    boolean existsByProjectIdAndUser_Id(UUID projectId, UUID userId);

    long countByProjectIdAndRole(UUID projectId, ProjectMemberRole role);

    long countByProjectId(UUID projectId);

    @Query("select m.project.id, count(m) from ProjectMember m where m.project.id in :projectIds group by m.project.id")
    List<Object[]> countGroupByProjectIds(@Param("projectIds") List<UUID> projectIds);
}
