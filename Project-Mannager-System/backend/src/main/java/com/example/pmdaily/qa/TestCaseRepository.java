package com.example.pmdaily.qa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TestCaseRepository extends JpaRepository<TestCase, UUID> {
    List<TestCase> findByProjectIdAndDeletedAtIsNull(UUID projectId);
    Optional<TestCase> findByIdAndDeletedAtIsNull(UUID id);
}
