package com.example.pmdaily.report;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportShareLinkRepository extends JpaRepository<ReportShareLink, UUID> {

    Optional<ReportShareLink> findByIdAndDeletedAtIsNull(UUID id);

    List<ReportShareLink> findByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID projectId);
}
