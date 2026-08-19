package com.example.pmdaily.git;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import com.example.pmdaily.task.Task;

@Getter
@Setter
@Entity
@Table(name = "task_git_commits")
public class GitCommit {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Column(name = "commit_hash", nullable = false, length = 100)
    private String commitHash;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "author", length = 100)
    private String author;

    @Column(name = "commit_url", length = 255)
    private String commitUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
