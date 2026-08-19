package com.example.pmdaily.meeting;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingParticipantRepository extends JpaRepository<MeetingParticipant, UUID> {

    List<MeetingParticipant> findByMeetingIdOrderByCreatedAtAsc(UUID meetingId);

    boolean existsByMeetingIdAndUser_Id(UUID meetingId, UUID userId);

    void deleteByMeetingId(UUID meetingId);

    void deleteByMeetingIdAndUser_Id(UUID meetingId, UUID userId);
}
