package com.example.pmdaily.sprint;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SprintRepository extends JpaRepository<Sprint, UUID> {
    List<Sprint> findByProjectIdAndDeletedAtIsNull(UUID projectId);
    Optional<Sprint> findByIdAndDeletedAtIsNull(UUID id);
    boolean existsByProjectIdAndStatusAndDeletedAtIsNull(UUID projectId, SprintStatus status);
}
