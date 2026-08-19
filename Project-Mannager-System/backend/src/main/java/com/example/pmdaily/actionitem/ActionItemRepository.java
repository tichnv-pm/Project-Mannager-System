package com.example.pmdaily.actionitem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ActionItemRepository
        extends JpaRepository<ActionItem, UUID>, JpaSpecificationExecutor<ActionItem> {

    @EntityGraph(attributePaths = {"meeting", "project", "assignee", "linkedTask"})
    @Override
    Optional<ActionItem> findById(UUID id);

    @EntityGraph(attributePaths = {"meeting", "project", "assignee"})
    @Override
    Page<ActionItem> findAll(Specification<ActionItem> spec, Pageable pageable);

    List<ActionItem> findByMeetingIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID meetingId);
}
