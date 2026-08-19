package com.example.pmdaily.project;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, UUID>, JpaSpecificationExecutor<Project> {

    java.util.List<Project> findByStatusAndDeletedAtIsNull(ProjectStatus status);

    @EntityGraph(attributePaths = {"projectManager"})
    @Override
    Optional<Project> findById(UUID id);

    @EntityGraph(attributePaths = {"projectManager"})
    @Override
    Page<Project> findAll(Specification<Project> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"projectManager"})
    Optional<Project> findByCode(String code);

    @EntityGraph(attributePaths = {"projectManager"})
    Optional<Project> findByCodeIgnoreCase(String code);

    boolean existsByCode(String code);

    @Query("select count(p) from Project p join p.members m where m.user.id = :userId and p.deletedAt is null")
    long countByMemberUserId(@Param("userId") UUID userId);

    /**
     * BR-PROJ-09: đếm task chưa đóng của dự án (native — bảng tasks chưa có entity ở Prompt 10).
     */
    @Query(value = """
            select count(t.id) from tasks t
            where t.project_id = :projectId and t.deleted_at is null
              and t.status <> 'DONE' and t.status <> 'CANCELLED'
            """, nativeQuery = true)
    long countOpenTasks(@Param("projectId") UUID projectId);
}
