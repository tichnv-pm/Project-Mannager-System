package com.example.pmdaily.wiki;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.example.pmdaily.common.BaseEntity;

@Entity
@Table(name = "project_wiki_page_histories")
@Getter
@Setter
@NoArgsConstructor
public class ProjectWikiPageHistory extends BaseEntity {

    @Column(name = "wiki_page_id", nullable = false)
    private UUID wikiPageId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "changed_by")
    private UUID changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt = Instant.now();
}
