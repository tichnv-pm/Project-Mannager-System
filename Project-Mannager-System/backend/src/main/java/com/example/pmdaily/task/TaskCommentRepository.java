package com.example.pmdaily.task;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskCommentRepository extends JpaRepository<TaskComment, UUID> {

    @EntityGraph(attributePaths = {"task"})
    List<TaskComment> findByTaskIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID taskId);

    @EntityGraph(attributePaths = {"task"})
    java.util.Optional<TaskComment> findById(UUID id);

    long countByTaskIdAndDeletedAtIsNull(UUID taskId);
}
