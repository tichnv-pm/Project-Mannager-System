package com.example.pmdaily.task.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.example.pmdaily.task.Tag;
import com.example.pmdaily.task.Task;
import com.example.pmdaily.task.TaskComment;
import com.example.pmdaily.task.dto.AttachmentResponse;
import com.example.pmdaily.task.dto.CommentResponse;
import com.example.pmdaily.task.dto.TagBriefResponse;
import com.example.pmdaily.task.dto.TaskResponse;
import com.example.pmdaily.task.dto.TaskSummaryResponse;
import com.example.pmdaily.task.dto.UserBriefResponse;
import com.example.pmdaily.user.User;

/**
 * Task ↔ DTO (docs/design/02-backend-architecture.md muc 4).
 * Tags/collaborators/watchers/commentCount/attachmentCount map thủ công trong service
 * (tránh lazy-load tập con — docs/design/02 muc 4).
 */
@Mapper(componentModel = "spring")
public interface TaskMapper {

    @Mapping(target = "projectId", source = "task.project.id")
    @Mapping(target = "projectCode", source = "task.project.code")
    @Mapping(target = "projectName", source = "task.project.name")
    @Mapping(target = "parentTaskId", source = "task.parentTask.id")
    @Mapping(target = "assignee", source = "task.assignee")
    @Mapping(target = "reporter", source = "task.reporter")
    TaskResponse toResponse(Task task);

    @Mapping(target = "projectId", source = "task.project.id")
    @Mapping(target = "projectCode", source = "task.project.code")
    @Mapping(target = "projectName", source = "task.project.name")
    @Mapping(target = "parentTaskId", source = "task.parentTask.id")
    @Mapping(target = "assignee", source = "task.assignee")
    TaskSummaryResponse toSummary(Task task);

    UserBriefResponse toUserBrief(User user);

    TagBriefResponse toTagBrief(Tag tag);

    @Mapping(target = "id", source = "comment.id")
    @Mapping(target = "createdAt", source = "comment.createdAt")
    @Mapping(target = "updatedAt", source = "comment.updatedAt")
    @Mapping(target = "author", source = "author")
    CommentResponse toCommentResponse(TaskComment comment, User author);

    @Mapping(target = "id", source = "attachment.id")
    @Mapping(target = "createdAt", source = "attachment.createdAt")
    @Mapping(target = "uploadedBy", source = "uploadedBy")
    @Mapping(target = "filePath", expression = "java(downloadUrl)")
    AttachmentResponse toAttachmentResponse(
            com.example.pmdaily.task.Attachment attachment, User uploadedBy, String downloadUrl);

    List<TaskSummaryResponse> toSummaries(List<Task> tasks);
}
