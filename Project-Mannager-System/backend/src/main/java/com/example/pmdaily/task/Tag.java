package com.example.pmdaily.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

import com.example.pmdaily.common.BaseAuditEntity;

/**
 * Thẻ gắn công việc (bảng tags) — uk_tags_name (tên duy nhất toàn hệ thống).
 */
@Getter
@Setter
@Entity
@Table(name = "tags")
public class Tag extends BaseAuditEntity {

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "color", length = 20)
    private String color;
}
