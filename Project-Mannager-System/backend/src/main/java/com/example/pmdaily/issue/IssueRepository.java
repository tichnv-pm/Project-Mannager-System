package com.example.pmdaily.issue;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface IssueRepository extends JpaRepository<Issue, UUID>, JpaSpecificationExecutor<Issue> {

    boolean existsByCode(String code);

    java.util.List<Issue> findByTestCaseIdAndDeletedAtIsNull(UUID testCaseId);
}
