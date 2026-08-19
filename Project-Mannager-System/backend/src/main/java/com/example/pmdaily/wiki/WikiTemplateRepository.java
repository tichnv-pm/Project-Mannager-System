package com.example.pmdaily.wiki;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WikiTemplateRepository extends JpaRepository<WikiTemplate, UUID> {
    List<WikiTemplate> findAllByOrderBySequenceNoAsc();
}
