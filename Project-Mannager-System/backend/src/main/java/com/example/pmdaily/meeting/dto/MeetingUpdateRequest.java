package com.example.pmdaily.meeting.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.example.pmdaily.meeting.MeetingStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cập nhật cuộc họp (docs/api/06-meeting-api.md muc 3.5, FR-MEET-02).
 * Đầy đủ field như Create + version (optimistic locking — 409 khi lỗi thời).
 */
public record MeetingUpdateRequest(
        @NotNull UUID projectId,
        @NotBlank @Size(max = 200) String title,
        @NotNull Instant startTime,
        @NotNull Instant endTime,
        @Size(max = 255) String location,
        @Size(max = 500) String meetingLink,
        @NotNull UUID chairpersonId,
        List<UUID> participantIds,
        String agenda,
        MeetingStatus status,
        @NotNull Long version) {
}
