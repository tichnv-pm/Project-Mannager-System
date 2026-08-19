package com.example.pmdaily.qa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TestRunRepository extends JpaRepository<TestRun, UUID> {
    List<TestRun> findByProjectIdAndDeletedAtIsNull(UUID projectId);
    Optional<TestRun> findByIdAndDeletedAtIsNull(UUID id);
}
