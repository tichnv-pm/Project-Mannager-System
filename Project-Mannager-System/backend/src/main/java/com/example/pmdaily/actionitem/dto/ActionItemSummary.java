package com.example.pmdaily.actionitem.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.example.pmdaily.actionitem.ActionItemStatus;
import com.example.pmdaily.task.dto.UserBriefResponse;

/**
 * Tóm tắt action item trong chi tiết cuộc họp (MeetingResponse.actionItems —
 * docs/api/06-meeting-api.md muc 3.4, ActionItemSummary trong openapi).
 */
public record ActionItemSummary(
        UUID id,
        String title,
        UserBriefResponse assignee,
        LocalDate dueDate,
        ActionItemStatus status) {
}
