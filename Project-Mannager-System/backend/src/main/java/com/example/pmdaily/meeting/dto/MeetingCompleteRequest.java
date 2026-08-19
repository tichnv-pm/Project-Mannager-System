package com.example.pmdaily.meeting.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Hoàn thành cuộc họp, khóa biên bản (docs/api/06-meeting-api.md muc 3.6, FR-MEET-05).
 * conclusion bắt buộc; content (nội dung diễn biến) tùy chọn.
 */
public record MeetingCompleteRequest(
        String content,
        @NotBlank String conclusion) {
}
