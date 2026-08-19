package com.example.pmdaily.meeting.dto;

import java.util.List;
import java.util.UUID;

/**
 * Thêm/bớt người tham gia cuộc họp (docs/api/06-meeting-api.md muc 3.7, FR-MEET-02).
 * BR-MEET-03: không trùng, phải thuộc dự án (kiểm tra trong service).
 */
public record MeetingParticipantsRequest(
        List<UUID> add,
        List<UUID> remove) {
}
