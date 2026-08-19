package com.example.pmdaily.wiki;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.pmdaily.security.UserPrincipal;
import com.example.pmdaily.wiki.dto.WikiPageCreateRequest;
import com.example.pmdaily.wiki.dto.WikiPageResponse;
import com.example.pmdaily.wiki.dto.WikiPageUpdateRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class ProjectWikiController {

    private final ProjectWikiService wikiService;

    public ProjectWikiController(ProjectWikiService wikiService) {
        this.wikiService = wikiService;
    }

    @GetMapping("/projects/{projectId}/wiki")
    @PreAuthorize("hasAuthority('project:view')")
    public List<WikiPageResponse> list(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID projectId) {
        return wikiService.getWikiPages(projectId, actor);
    }

    @GetMapping("/wiki-pages/{id}")
    @PreAuthorize("hasAuthority('project:view')")
    public WikiPageResponse get(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id) {
        return wikiService.getWikiPage(id, actor);
    }

    @PostMapping("/projects/{projectId}/wiki/initialize")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('project:update')")
    public void initialize(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID projectId) {
        wikiService.initializeWiki(projectId, actor);
    }

    @PostMapping("/projects/{projectId}/wiki")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('project:update')")
    public WikiPageResponse create(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID projectId,
            @Valid @RequestBody WikiPageCreateRequest request) {
        return wikiService.createWikiPage(projectId, request, actor);
    }

    @PutMapping("/wiki-pages/{id}")
    @PreAuthorize("hasAuthority('project:update')")
    public WikiPageResponse update(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id,
            @Valid @RequestBody WikiPageUpdateRequest request) {
        return wikiService.updateWikiPage(id, request, actor);
    }

    @DeleteMapping("/wiki-pages/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('project:update')")
    public void delete(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id) {
        wikiService.deleteWikiPage(id, actor);
    }
}
