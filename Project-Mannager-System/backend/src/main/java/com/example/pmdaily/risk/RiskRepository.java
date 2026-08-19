package com.example.pmdaily.risk;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface RiskRepository extends JpaRepository<Risk, UUID>, JpaSpecificationExecutor<Risk> {

    boolean existsByCode(String code);

    boolean existsByLinkedIssueId(UUID linkedIssueId);
}
