package com.example.pmdaily.git;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GitCommitRepository extends JpaRepository<GitCommit, UUID> {
    List<GitCommit> findByTaskIdOrderByCreatedAtDesc(UUID taskId);
    boolean existsByCommitHash(String commitHash);
    boolean existsByTaskId(UUID taskId);
}
