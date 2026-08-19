package com.example.pmdaily.task;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskWatcherRepository extends JpaRepository<TaskWatcher, UUID> {

    @EntityGraph(attributePaths = {"user"})
    List<TaskWatcher> findByTaskIdOrderByCreatedAtAsc(UUID taskId);

    void deleteByTaskId(UUID taskId);
}
