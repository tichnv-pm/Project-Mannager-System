package com.example.pmdaily.task;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    List<Attachment> findByTaskIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID taskId);

    List<Attachment> findByMeetingIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID meetingId);

    java.util.Optional<Attachment> findById(UUID id);

    long countByTaskIdAndDeletedAtIsNull(UUID taskId);
}
