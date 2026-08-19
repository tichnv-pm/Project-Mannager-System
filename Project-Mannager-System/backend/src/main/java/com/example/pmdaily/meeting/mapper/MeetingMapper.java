package com.example.pmdaily.meeting.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.example.pmdaily.actionitem.dto.ActionItemSummary;
import com.example.pmdaily.meeting.Meeting;
import com.example.pmdaily.meeting.dto.MeetingResponse;
import com.example.pmdaily.task.Attachment;
import com.example.pmdaily.task.dto.AttachmentResponse;
import com.example.pmdaily.task.dto.UserBriefResponse;
import com.example.pmdaily.user.User;

/**
 * Meeting ↔ DTO (docs/design/02-backend-architecture.md muc 4).
 * chairperson/participants/attachments/actionItems map thủ công trong service
 * (tránh lazy-load tập con — docs/design/02 muc 4).
 */
@Mapper(componentModel = "spring")
public interface MeetingMapper {

    @Mapping(target = "id", source = "meeting.id")
    @Mapping(target = "projectId", source = "meeting.project.id")
    @Mapping(target = "projectCode", source = "meeting.project.code")
    @Mapping(target = "projectName", source = "meeting.project.name")
    @Mapping(target = "createdAt", source = "meeting.createdAt")
    @Mapping(target = "chairperson", source = "chairperson")
    @Mapping(target = "participants", source = "participants")
    @Mapping(target = "attachments", source = "attachments")
    @Mapping(target = "actionItems", source = "actionItems")
    MeetingResponse toResponse(Meeting meeting, UserBriefResponse chairperson,
            List<UserBriefResponse> participants, List<AttachmentResponse> attachments,
            List<ActionItemSummary> actionItems);

    UserBriefResponse toUserBrief(User user);

    @Mapping(target = "id", source = "attachment.id")
    @Mapping(target = "createdAt", source = "attachment.createdAt")
    @Mapping(target = "uploadedBy", source = "uploadedBy")
    @Mapping(target = "filePath", expression = "java(downloadUrl)")
    AttachmentResponse toAttachmentResponse(Attachment attachment, User uploadedBy, String downloadUrl);
}
