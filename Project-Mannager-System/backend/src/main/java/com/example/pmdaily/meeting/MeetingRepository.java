package com.example.pmdaily.meeting;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MeetingRepository extends JpaRepository<Meeting, UUID>, JpaSpecificationExecutor<Meeting> {

    @EntityGraph(attributePaths = {"project", "chairperson"})
    @Override
    Optional<Meeting> findById(UUID id);

    @EntityGraph(attributePaths = {"project", "chairperson"})
    @Override
    Page<Meeting> findAll(Specification<Meeting> spec, Pageable pageable);
}
