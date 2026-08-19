package com.example.pmdaily.wiki;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.example.pmdaily.common.BaseEntity;

@Entity
@Table(name = "wiki_page_templates")
@Getter
@Setter
@NoArgsConstructor
public class WikiTemplate extends BaseEntity {

    @Column(name = "parent_template_id")
    private UUID parentTemplateId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "content_placeholder", nullable = false)
    private String contentPlaceholder;

    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo = 0;
}
