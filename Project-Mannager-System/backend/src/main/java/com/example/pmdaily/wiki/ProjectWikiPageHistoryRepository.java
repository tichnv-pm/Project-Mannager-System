package com.example.pmdaily.wiki;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectWikiPageHistoryRepository extends JpaRepository<ProjectWikiPageHistory, UUID> {
    List<ProjectWikiPageHistory> findByWikiPageIdOrderByChangedAtDesc(UUID wikiPageId);
}
