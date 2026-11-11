package com.example.pmdaily.dod;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import com.example.pmdaily.common.BaseAuditEntity;

/**
 * 1 tiêu chí Definition of Done của dự án (checklist PM định nghĩa, GĐ3 cải tiến).
 * Không theo dõi trạng thái check từng item/từng task — xem gate ở TaskService.changeStatus().
 */
@Getter
@Setter
@Entity
@Table(name = "project_dod_items")
public class ProjectDodItem extends BaseAuditEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "text", nullable = false, length = 500)
    private String text;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
