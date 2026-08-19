package com.example.pmdaily.actionitem.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.example.pmdaily.actionitem.ActionItem;
import com.example.pmdaily.actionitem.dto.ActionItemResponse;
import com.example.pmdaily.actionitem.dto.ActionItemSummary;
import com.example.pmdaily.task.dto.UserBriefResponse;
import com.example.pmdaily.user.User;

/**
 * ActionItem ↔ DTO (docs/design/02-backend-architecture.md muc 4).
 * assignee/meetingId/projectId map thủ công trong service (tránh lazy-load tập con).
 */
@Mapper(componentModel = "spring")
public interface ActionItemMapper {

    @Mapping(target = "id", source = "item.id")
    @Mapping(target = "meetingId", source = "item.meeting.id")
    @Mapping(target = "projectId", source = "item.project.id")
    @Mapping(target = "createdAt", source = "item.createdAt")
    @Mapping(target = "linkedTaskId", source = "item.linkedTask.id")
    @Mapping(target = "assignee", source = "assignee")
    ActionItemResponse toResponse(ActionItem item, UserBriefResponse assignee);

    @Mapping(target = "id", source = "item.id")
    @Mapping(target = "assignee", source = "assignee")
    ActionItemSummary toSummary(ActionItem item, UserBriefResponse assignee);

    UserBriefResponse toUserBrief(User user);
}
