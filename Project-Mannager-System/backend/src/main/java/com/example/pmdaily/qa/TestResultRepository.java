package com.example.pmdaily.qa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TestResultRepository extends JpaRepository<TestResult, UUID> {
    List<TestResult> findByTestRunId(UUID testRunId);
    Optional<TestResult> findByTestRunIdAndTestCaseId(UUID testRunId, UUID testCaseId);
    void deleteByTestRunId(UUID testRunId);
}
