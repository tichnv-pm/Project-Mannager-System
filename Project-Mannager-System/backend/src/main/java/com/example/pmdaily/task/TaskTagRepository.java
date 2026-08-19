package com.example.pmdaily.task;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskTagRepository extends JpaRepository<TaskTag, UUID> {

    @EntityGraph(attributePaths = {"tag"})
    List<TaskTag> findByTaskIdOrderByCreatedAtAsc(UUID taskId);

    void deleteByTaskId(UUID taskId);
}
