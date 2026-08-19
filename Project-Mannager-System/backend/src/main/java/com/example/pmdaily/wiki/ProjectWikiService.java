package com.example.pmdaily.wiki;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.pmdaily.audit.AuditService;
import com.example.pmdaily.exception.ConflictException;
import com.example.pmdaily.exception.ResourceNotFoundException;
import com.example.pmdaily.project.ProjectMember;
import com.example.pmdaily.project.ProjectMemberRepository;
import com.example.pmdaily.project.ProjectMemberRole;
import com.example.pmdaily.security.UserPrincipal;
import com.example.pmdaily.wiki.dto.WikiPageCreateRequest;
import com.example.pmdaily.wiki.dto.WikiPageResponse;
import com.example.pmdaily.wiki.dto.WikiPageUpdateRequest;

@Service
@Transactional(readOnly = true)
public class ProjectWikiService {

    private final WikiTemplateRepository templateRepository;
    private final ProjectWikiPageRepository wikiPageRepository;
    private final ProjectWikiPageHistoryRepository wikiPageHistoryRepository;
    private final ProjectMemberRepository memberRepository;
    private final AuditService auditService;

    public ProjectWikiService(
            WikiTemplateRepository templateRepository,
            ProjectWikiPageRepository wikiPageRepository,
            ProjectWikiPageHistoryRepository wikiPageHistoryRepository,
            ProjectMemberRepository memberRepository,
            AuditService auditService) {
        this.templateRepository = templateRepository;
        this.wikiPageRepository = wikiPageRepository;
        this.wikiPageHistoryRepository = wikiPageHistoryRepository;
        this.memberRepository = memberRepository;
        this.auditService = auditService;
    }

    private void checkProjectViewAccess(UserPrincipal actor, UUID projectId) {
        if (actor.getRoles().contains("ADMIN")) {
            return;
        }
        if (!memberRepository.existsByProjectIdAndUser_Id(projectId, actor.getId())) {
            throw new AccessDeniedException("Access denied to project");
        }
    }

    private void checkProjectManageAccess(UserPrincipal actor, UUID projectId) {
        if (actor.getRoles().contains("ADMIN")) {
            return;
        }
        ProjectMember member = memberRepository.findByProjectIdAndUser_Id(projectId, actor.getId())
                .orElseThrow(() -> new AccessDeniedException("Access denied to project"));
        if (member.getRole() != ProjectMemberRole.PROJECT_MANAGER) {
            throw new AccessDeniedException("Cần quyền PROJECT_MANAGER của dự án");
        }
    }

    public List<WikiPageResponse> getWikiPages(UUID projectId, UserPrincipal actor) {
        checkProjectViewAccess(actor, projectId);
        return wikiPageRepository.findByProjectIdAndDeletedAtIsNull(projectId).stream()
                .map(this::toResponse)
                .toList();
    }

    public WikiPageResponse getWikiPage(UUID pageId, UserPrincipal actor) {
        ProjectWikiPage page = wikiPageRepository.findByIdAndDeletedAtIsNull(pageId)
                .orElseThrow(() -> new ResourceNotFoundException("Wiki page", pageId));
        checkProjectViewAccess(actor, page.getProjectId());
        return toResponse(page);
    }

    @Transactional
    public void initializeWiki(UUID projectId, UserPrincipal actor) {
        checkProjectManageAccess(actor, projectId);

        // Check if wiki is already initialized
        List<ProjectWikiPage> existingPages = wikiPageRepository.findByProjectIdAndDeletedAtIsNull(projectId);
        if (!existingPages.isEmpty()) {
            throw new ConflictException("Wiki đã được khởi tạo cho dự án này");
        }

        // Fetch all templates sorted by sequence
        List<WikiTemplate> templates = templateRepository.findAllByOrderBySequenceNoAsc();

        // Map: oldTemplateId -> newWikiPageId
        Map<UUID, UUID> oldToNewMap = new HashMap<>();

        for (WikiTemplate template : templates) {
            UUID parentId = null;
            if (template.getParentTemplateId() != null) {
                parentId = oldToNewMap.get(template.getParentTemplateId());
            }

            ProjectWikiPage page = new ProjectWikiPage();
            page.setProjectId(projectId);
            page.setParentPageId(parentId);
            page.setTitle(template.getTitle());
            page.setContent(template.getContentPlaceholder());
            page.setCreatedBy(actor.getId());
            page.setUpdatedBy(actor.getId());

            ProjectWikiPage savedPage = wikiPageRepository.save(page);
            oldToNewMap.put(template.getId(), savedPage.getId());
        }

        auditService.record("WIKI_INITIALIZED", "PROJECT", projectId, Map.of("projectId", projectId.toString()));
    }

    @Transactional
    public WikiPageResponse createWikiPage(UUID projectId, WikiPageCreateRequest request, UserPrincipal actor) {
        checkProjectManageAccess(actor, projectId);

        if (wikiPageRepository.existsByProjectIdAndTitleAndDeletedAtIsNull(projectId, request.title().trim())) {
            throw new ConflictException("Tiêu đề trang wiki đã tồn tại trong dự án");
        }

        if (request.parentPageId() != null) {
            ProjectWikiPage parent = wikiPageRepository.findByIdAndDeletedAtIsNull(request.parentPageId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent wiki page", request.parentPageId()));
            if (!Objects.equals(parent.getProjectId(), projectId)) {
                throw new AccessDeniedException("Trang cha phải thuộc cùng một dự án");
            }
        }

        ProjectWikiPage page = new ProjectWikiPage();
        page.setProjectId(projectId);
        page.setParentPageId(request.parentPageId());
        page.setTitle(request.title().trim());
        page.setContent(request.content());
        page.setCreatedBy(actor.getId());
        page.setUpdatedBy(actor.getId());

        ProjectWikiPage saved = wikiPageRepository.save(page);

        // Save history entry
        saveHistory(saved, actor.getId());

        auditService.record("WIKI_PAGE_CREATED", "WIKI_PAGE", saved.getId(), Map.of("title", saved.getTitle(), "projectId", projectId.toString()));
        return toResponse(saved);
    }

    @Transactional
    public WikiPageResponse updateWikiPage(UUID pageId, WikiPageUpdateRequest request, UserPrincipal actor) {
        ProjectWikiPage page = wikiPageRepository.findByIdAndDeletedAtIsNull(pageId)
                .orElseThrow(() -> new ResourceNotFoundException("Wiki page", pageId));
        checkProjectManageAccess(actor, page.getProjectId());

        if (page.getVersion() != request.version()) {
            throw new ConflictException("Xung đột phiên bản: Trang đã được cập nhật bởi một người dùng khác");
        }

        // Check title uniqueness if title changed
        String newTitle = request.title().trim();
        if (!Objects.equals(page.getTitle(), newTitle)) {
            if (wikiPageRepository.existsByProjectIdAndTitleAndDeletedAtIsNull(page.getProjectId(), newTitle)) {
                throw new ConflictException("Tiêu đề trang wiki đã tồn tại trong dự án");
            }
            page.setTitle(newTitle);
        }

        page.setContent(request.content());
        page.setUpdatedBy(actor.getId());
        page.setUpdatedAt(Instant.now());

        ProjectWikiPage saved = wikiPageRepository.saveAndFlush(page);

        // Save history entry
        saveHistory(saved, actor.getId());

        auditService.record("WIKI_PAGE_UPDATED", "WIKI_PAGE", saved.getId(), Map.of("title", saved.getTitle()));
        return toResponse(saved);
    }

    @Transactional
    public void deleteWikiPage(UUID pageId, UserPrincipal actor) {
        ProjectWikiPage page = wikiPageRepository.findByIdAndDeletedAtIsNull(pageId)
                .orElseThrow(() -> new ResourceNotFoundException("Wiki page", pageId));
        checkProjectManageAccess(actor, page.getProjectId());

        page.setDeletedAt(Instant.now());
        page.setDeletedBy(actor.getId());
        wikiPageRepository.save(page);

        auditService.record("WIKI_PAGE_DELETED", "WIKI_PAGE", page.getId(), Map.of("title", page.getTitle()));
    }

    private void saveHistory(ProjectWikiPage page, UUID actorId) {
        ProjectWikiPageHistory history = new ProjectWikiPageHistory();
        history.setWikiPageId(page.getId());
        history.setTitle(page.getTitle());
        history.setContent(page.getContent());
        history.setChangedBy(actorId);
        history.setChangedAt(Instant.now());
        wikiPageHistoryRepository.save(history);
    }

    private WikiPageResponse toResponse(ProjectWikiPage page) {
        return new WikiPageResponse(
                page.getId(),
                page.getProjectId(),
                page.getParentPageId(),
                page.getTitle(),
                page.getContent(),
                page.getVersion(),
                page.getCreatedAt(),
                page.getCreatedBy(),
                page.getUpdatedAt(),
                page.getUpdatedBy()
        );
    }
}
