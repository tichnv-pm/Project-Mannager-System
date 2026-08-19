package com.example.pmdaily.meeting;

/**
 * Trạng thái cuộc họp (docs/api/06-meeting-api.md, BR-MEET-06).
 * CANCELLED là trạng thái cuối — họp đã hủy không chuyển trạng thái khác.
 */
public enum MeetingStatus {
    SCHEDULED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
