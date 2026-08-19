package com.example.pmdaily.risk.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.example.pmdaily.risk.Risk;
import com.example.pmdaily.risk.dto.RiskResponse;
import com.example.pmdaily.task.dto.UserBriefResponse;
import com.example.pmdaily.user.User;

@Mapper(componentModel = "spring")
public interface RiskMapper {

    @Mapping(target = "id", source = "risk.id")
    @Mapping(target = "projectId", source = "risk.project.id")
    @Mapping(target = "owner", source = "owner")
    @Mapping(target = "linkedIssueId", source = "risk.linkedIssue.id")
    @Mapping(target = "createdAt", source = "risk.createdAt")
    RiskResponse toResponse(Risk risk, UserBriefResponse owner);

    UserBriefResponse toUserBrief(User user);
}
