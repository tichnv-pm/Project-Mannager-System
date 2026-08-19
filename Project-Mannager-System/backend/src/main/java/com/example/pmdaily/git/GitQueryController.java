package com.example.pmdaily.git;

import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.example.pmdaily.security.UserPrincipal;
import com.example.pmdaily.task.Task;
import com.example.pmdaily.task.TaskRepository;
import com.example.pmdaily.exception.ResourceNotFoundException;
import com.example.pmdaily.project.ProjectMemberRepository;
import com.example.pmdaily.git.dto.GitCommitResponse;
import com.example.pmdaily.git.dto.GitPullRequestResponse;

@RestController
@RequestMapping("/api/v1/tasks")
public class GitQueryController {

    private final GitIntegrationService gitIntegrationService;
    private final TaskRepository taskRepository;
    private final ProjectMemberRepository memberRepository;

    public GitQueryController(GitIntegrationService gitIntegrationService, 
                              TaskRepository taskRepository,
                              ProjectMemberRepository memberRepository) {
        this.gitIntegrationService = gitIntegrationService;
        this.taskRepository = taskRepository;
        this.memberRepository = memberRepository;
    }

    @GetMapping("/{taskId}/git")
    @PreAuthorize("hasAuthority('task:view')")
    public GitInfoResponse getGitInfo(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID taskId) {
        
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Công việc", taskId));
        
        if (!actor.getRoles().contains("ADMIN") && 
            !memberRepository.existsByProjectIdAndUser_Id(task.getProject().getId(), actor.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Không có quyền truy cập dự án");
        }

        List<GitCommitResponse> commits = gitIntegrationService.getCommits(taskId).stream()
                .map(c -> new GitCommitResponse(c.getId(), c.getCommitHash(), c.getMessage(), c.getAuthor(), c.getCommitUrl(), c.getCreatedAt()))
                .toList();

        List<GitPullRequestResponse> pullRequests = gitIntegrationService.getPullRequests(taskId).stream()
                .map(pr -> new GitPullRequestResponse(pr.getId(), pr.getPrNumber(), pr.getTitle(), pr.getStatus(), pr.getPrUrl(), pr.getCreatedAt(), pr.getUpdatedAt()))
                .toList();

        return new GitInfoResponse(commits, pullRequests);
    }

    public record GitInfoResponse(
        List<GitCommitResponse> commits,
        List<GitPullRequestResponse> pullRequests
    ) {}
}
