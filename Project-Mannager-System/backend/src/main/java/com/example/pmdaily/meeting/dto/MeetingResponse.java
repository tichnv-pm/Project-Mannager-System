package com.example.pmdaily.meeting.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.example.pmdaily.actionitem.dto.ActionItemSummary;
import com.example.pmdaily.meeting.MeetingStatus;
import com.example.pmdaily.task.dto.AttachmentResponse;
import com.example.pmdaily.task.dto.UserBriefResponse;

/**
 * Response cuộc họp (docs/api/06-meeting-api.md muc 3.3/3.4).
 * actionItems/attachments được điền đầy đủ ở chi tiết (3.4); ở danh sách là danh sách rỗng.
 */
public record MeetingResponse(
        UUID id,
        UUID projectId,
        String projectCode,
        String projectName,
        String title,
        Instant startTime,
        Instant endTime,
        String location,
        String meetingLink,
        UserBriefResponse chairperson,
        List<UserBriefResponse> participants,
        MeetingStatus status,
        String agenda,
        String content,
        String conclusion,
        List<AttachmentResponse> attachments,
        List<ActionItemSummary> actionItems,
        Instant createdAt,
        long version) {
}
