package com.example.pmdaily.plan;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.example.pmdaily.common.BaseEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "plan_templates")
@Getter
@Setter
@NoArgsConstructor
public class PlanTemplate extends BaseEntity {

    @Column(name = "template_code", nullable = false, unique = true, length = 50)
    private String templateCode;

    @Column(name = "template_name", nullable = false)
    private String templateName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "template_type", nullable = false, length = 20)
    private TemplateType templateType = TemplateType.FULL;

    @Column(name = "category", nullable = false, length = 50)
    private String category = "SOFTWARE";

    @Column(name = "version_no", nullable = false)
    private Integer versionNo = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TemplateStatus status = TemplateStatus.PUBLISHED;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "is_built_in", nullable = false)
    private Boolean isBuiltIn = true;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceNo ASC")
    private List<PlanTemplateTask> tasks = new ArrayList<>();
}
