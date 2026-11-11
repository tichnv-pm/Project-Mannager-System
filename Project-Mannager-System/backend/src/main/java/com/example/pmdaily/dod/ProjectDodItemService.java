package com.example.pmdaily.dod;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.pmdaily.dod.dto.ProjectDodItemCreateRequest;
import com.example.pmdaily.dod.dto.ProjectDodItemResponse;
import com.example.pmdaily.dod.dto.ProjectDodItemUpdateRequest;
import com.example.pmdaily.exception.ConflictException;
import com.example.pmdaily.exception.ResourceNotFoundException;
import com.example.pmdaily.project.ProjectMember;
import com.example.pmdaily.project.ProjectMemberRepository;
import com.example.pmdaily.project.ProjectMemberRole;
import com.example.pmdaily.security.UserPrincipal;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProjectDodItemService {

    private final ProjectDodItemRepository repository;
    private final ProjectMemberRepository memberRepository;

    private void checkProjectViewAccess(UserPrincipal actor, UUID projectId) {
        if (actor.getRoles().contains("ADMIN")) {
            return;
        }
        if (!memberRepository.existsByProjectIdAndUser_Id(projectId, actor.getId())) {
            throw new AccessDeniedException("Không có quyền truy cập dự án");
        }
    }

    private void checkProjectManageAccess(UserPrincipal actor, UUID projectId) {
        if (actor.getRoles().contains("ADMIN")) {
            return;
        }
        ProjectMember member = memberRepository.findByProjectIdAndUser_Id(projectId, actor.getId())
                .orElseThrow(() -> new AccessDeniedException("Không có quyền truy cập dự án"));
        if (member.getRole() != ProjectMemberRole.PROJECT_MANAGER) {
            throw new AccessDeniedException("Cần quyền PROJECT_MANAGER của dự án để thao tác");
        }
    }

    @Transactional(readOnly = true)
    public List<ProjectDodItemResponse> list(UUID projectId, UserPrincipal actor) {
        checkProjectViewAccess(actor, projectId);
        return repository.findByProjectIdOrderByDisplayOrderAsc(projectId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ProjectDodItemResponse create(UUID projectId, ProjectDodItemCreateRequest request, UserPrincipal actor) {
        checkProjectManageAccess(actor, projectId);

        int nextOrder = repository.findByProjectIdOrderByDisplayOrderAsc(projectId).size();

        ProjectDodItem item = new ProjectDodItem();
        item.setProjectId(projectId);
        item.setText(request.text().trim());
        item.setDisplayOrder(nextOrder);
        item.setActive(true);
        item.setCreatedBy(actor.getId());
        item.setUpdatedBy(actor.getId());

        return toResponse(repository.save(item));
    }

    @Transactional
    public ProjectDodItemResponse update(UUID projectId, UUID itemId, ProjectDodItemUpdateRequest request,
            UserPrincipal actor) {
        checkProjectManageAccess(actor, projectId);
        ProjectDodItem item = repository.findByIdAndProjectId(itemId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Tiêu chí Definition of Done", itemId));

        if (item.getVersion() != request.version()) {
            throw new ConflictException();
        }

        item.setText(request.text().trim());
        item.setActive(request.active());
        item.setUpdatedBy(actor.getId());

        return toResponse(repository.save(item));
    }

    @Transactional
    public void delete(UUID projectId, UUID itemId, UserPrincipal actor) {
        checkProjectManageAccess(actor, projectId);
        ProjectDodItem item = repository.findByIdAndProjectId(itemId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Tiêu chí Definition of Done", itemId));
        repository.delete(item);
    }

    private ProjectDodItemResponse toResponse(ProjectDodItem item) {
        return new ProjectDodItemResponse(item.getId(), item.getProjectId(), item.getText(),
                item.getDisplayOrder(), item.isActive(), item.getVersion());
    }
}
