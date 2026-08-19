package com.example.pmdaily.git;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.pmdaily.audit.AuditService;
import com.example.pmdaily.task.Task;
import com.example.pmdaily.task.TaskRepository;
import com.example.pmdaily.task.TaskStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GitIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(GitIntegrationService.class);

    // Matches commit messages like "[PRJ-TASK-1] title"
    private static final Pattern COMMIT_PATTERN = Pattern.compile("^\\[([A-Z0-9]+-TASK-\\d+)\\]\\s*(.*)$");
    private static final Pattern GENERAL_PATTERN = Pattern.compile("\\[([A-Z0-9]+-TASK-\\d+)\\]");

    private final GitCommitRepository gitCommitRepository;
    private final GitPullRequestRepository gitPullRequestRepository;
    private final TaskRepository taskRepository;
    private final AuditService auditService;

    @Transactional
    public void processCommit(String hash, String message, String author, String url) {
        String taskCode = extractTaskCode(message);
        if (taskCode == null) {
            log.debug("Commit message does not match task pattern: {}", message);
            return;
        }

        Optional<Task> taskOpt = taskRepository.findByCodeAndDeletedAtIsNull(taskCode);
        if (taskOpt.isEmpty()) {
            log.warn("Task not found for code: {}", taskCode);
            return;
        }

        Task task = taskOpt.get();
        if (!gitCommitRepository.existsByCommitHash(hash)) {
            GitCommit commit = new GitCommit();
            commit.setTask(task);
            commit.setCommitHash(hash);
            commit.setMessage(message);
            commit.setAuthor(author);
            commit.setCommitUrl(url);
            gitCommitRepository.save(commit);

            auditService.record("GIT_COMMIT_LINKED", "TASK", task.getId(),
                    Map.of("code", task.getCode(), "commitHash", hash, "author", author));
            log.info("Linked git commit {} to task {}", hash, task.getCode());
        }
    }

    @Transactional
    public void processGitHubPullRequest(int number, String title, String state, boolean merged, String url) {
        String taskCode = extractTaskCode(title);
        if (taskCode == null) {
            log.debug("GitHub PR title does not match task pattern: {}", title);
            return;
        }

        Optional<Task> taskOpt = taskRepository.findByCodeAndDeletedAtIsNull(taskCode);
        if (taskOpt.isEmpty()) {
            log.warn("Task not found for code: {}", taskCode);
            return;
        }

        Task task = taskOpt.get();
        saveOrUpdatePullRequest(task, number, title, state, url, merged);
    }

    @Transactional
    public void processGitLabMergeRequest(int number, String title, String state, boolean merged, String url) {
        String taskCode = extractTaskCode(title);
        if (taskCode == null) {
            log.debug("GitLab MR title does not match task pattern: {}", title);
            return;
        }

        Optional<Task> taskOpt = taskRepository.findByCodeAndDeletedAtIsNull(taskCode);
        if (taskOpt.isEmpty()) {
            log.warn("Task not found for code: {}", taskCode);
            return;
        }

        Task task = taskOpt.get();
        saveOrUpdatePullRequest(task, number, title, state, url, merged);
    }

    private void saveOrUpdatePullRequest(Task task, int number, String title, String state, String url, boolean merged) {
        Optional<GitPullRequest> prOpt = gitPullRequestRepository.findByPrNumberAndTaskId(number, task.getId());
        
        GitPullRequest pr = prOpt.orElse(new GitPullRequest());
        pr.setTask(task);
        pr.setPrNumber(number);
        pr.setTitle(title);
        pr.setStatus(state.toUpperCase());
        pr.setPrUrl(url);
        pr.setUpdatedAt(Instant.now());
        if (prOpt.isEmpty()) {
            pr.setCreatedAt(Instant.now());
        }
        
        gitPullRequestRepository.save(pr);

        auditService.record("GIT_PR_LINKED", "TASK", task.getId(),
                Map.of("code", task.getCode(), "prNumber", String.valueOf(number), "status", state));
        log.info("Linked git PR #{} (status={}) to task {}", number, state, task.getCode());

        if (merged && task.getStatus() != TaskStatus.DONE) {
            TaskStatus oldStatus = task.getStatus();
            task.setStatus(TaskStatus.DONE);
            task.setProgress(100);
            if (task.getActualCompletedAt() == null) {
                task.setActualCompletedAt(Instant.now());
            }
            taskRepository.save(task);

            auditService.record("TASK_STATUS_CHANGE", "TASK", task.getId(),
                    Map.of("status", oldStatus.name()), Map.of("status", TaskStatus.DONE.name()));
            log.info("Auto-completed task {} as PR #{} was merged", task.getCode(), number);
        }
    }

    private String extractTaskCode(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = COMMIT_PATTERN.matcher(text.trim());
        if (matcher.find()) {
            return matcher.group(1);
        }
        Matcher generalMatcher = GENERAL_PATTERN.matcher(text);
        if (generalMatcher.find()) {
            return generalMatcher.group(1);
        }
        return null;
    }

    @Transactional(readOnly = true)
    public List<GitCommit> getCommits(UUID taskId) {
        return gitCommitRepository.findByTaskIdOrderByCreatedAtDesc(taskId);
    }

    @Transactional(readOnly = true)
    public List<GitPullRequest> getPullRequests(UUID taskId) {
        return gitPullRequestRepository.findByTaskIdOrderByCreatedAtDesc(taskId);
    }
}
