package com.example.pmdaily.git;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GitPullRequestRepository extends JpaRepository<GitPullRequest, UUID> {
    List<GitPullRequest> findByTaskIdOrderByCreatedAtDesc(UUID taskId);
    Optional<GitPullRequest> findByPrNumberAndTaskId(Integer prNumber, UUID taskId);
    boolean existsByTaskId(UUID taskId);
}
