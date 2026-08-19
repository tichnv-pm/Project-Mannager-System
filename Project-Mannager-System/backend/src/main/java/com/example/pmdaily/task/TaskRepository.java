package com.example.pmdaily.task;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TaskRepository extends JpaRepository<Task, UUID>, JpaSpecificationExecutor<Task> {

    @EntityGraph(attributePaths = {"project", "parentTask", "assignee", "reporter"})
    @Override
    Optional<Task> findById(UUID id);

    @EntityGraph(attributePaths = {"assignee", "reporter"})
    @Override
    Page<Task> findAll(Specification<Task> spec, Pageable pageable);

    boolean existsByCode(String code);

    Optional<Task> findByCodeAndDeletedAtIsNull(String code);

    List<Task> findByProjectIdAndDeletedAtIsNull(UUID projectId);

    long countByParentTaskIdAndDeletedAtIsNull(UUID parentTaskId);

    @EntityGraph(attributePaths = {"assignee", "reporter"})
    List<Task> findByParentTaskIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID parentTaskId);

    List<Task> findBySprintIdAndDeletedAtIsNull(UUID sprintId);
}
