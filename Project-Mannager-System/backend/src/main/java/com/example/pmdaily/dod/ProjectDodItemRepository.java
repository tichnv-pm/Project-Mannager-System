package com.example.pmdaily.dod;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectDodItemRepository extends JpaRepository<ProjectDodItem, UUID> {
    List<ProjectDodItem> findByProjectIdOrderByDisplayOrderAsc(UUID projectId);
    boolean existsByProjectIdAndActiveTrue(UUID projectId);
    Optional<ProjectDodItem> findByIdAndProjectId(UUID id, UUID projectId);
}
