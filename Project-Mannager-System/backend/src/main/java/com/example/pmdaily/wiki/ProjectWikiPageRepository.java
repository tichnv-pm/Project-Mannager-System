package com.example.pmdaily.wiki;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectWikiPageRepository extends JpaRepository<ProjectWikiPage, UUID> {
    List<ProjectWikiPage> findByProjectIdAndDeletedAtIsNull(UUID projectId);
    Optional<ProjectWikiPage> findByIdAndDeletedAtIsNull(UUID id);
    boolean existsByProjectIdAndTitleAndDeletedAtIsNull(UUID projectId, String title);
}
