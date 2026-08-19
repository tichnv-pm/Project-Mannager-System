package com.example.pmdaily.wiki;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.example.pmdaily.common.SoftDeleteEntity;

@Entity
@Table(name = "project_wiki_pages")
@Getter
@Setter
@NoArgsConstructor
public class ProjectWikiPage extends SoftDeleteEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "parent_page_id")
    private UUID parentPageId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "content", nullable = false)
    private String content;
}
