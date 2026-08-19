package com.example.pmdaily.issue.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.example.pmdaily.issue.Issue;
import com.example.pmdaily.issue.dto.IssueResponse;
import com.example.pmdaily.task.dto.UserBriefResponse;
import com.example.pmdaily.user.User;

@Mapper(componentModel = "spring")
public interface IssueMapper {

    @Mapping(target = "id", source = "issue.id")
    @Mapping(target = "projectId", source = "issue.project.id")
    @Mapping(target = "owner", source = "owner")
    @Mapping(target = "createdAt", source = "issue.createdAt")
    IssueResponse toResponse(Issue issue, UserBriefResponse owner);

    UserBriefResponse toUserBrief(User user);
}
