package com.example.pmdaily.project.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.example.pmdaily.project.Project;
import com.example.pmdaily.project.ProjectMember;
import com.example.pmdaily.project.dto.ProjectCreateRequest;
import com.example.pmdaily.project.dto.ProjectMemberResponse;
import com.example.pmdaily.project.dto.ProjectResponse;

/**
 * Project ↔ DTO (docs/design/02-backend-architecture.md muc 4).
 * memberCount map thủ công trong service (tránh lazy-load tập members — docs/design/02 muc 4).
 */
@Mapper(componentModel = "spring")
public interface ProjectMapper {

    Project toEntity(ProjectCreateRequest request);

    @Mapping(target = "projectManagerId", source = "projectManager.id")
    ProjectResponse toResponse(Project project);

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "username", source = "user.username")
    @Mapping(target = "fullName", source = "user.fullName")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "joinedAt", source = "createdAt")
    ProjectMemberResponse toMemberResponse(ProjectMember member);
}
