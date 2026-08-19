package com.example.pmdaily.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import com.example.pmdaily.common.BaseAuditEntity;

/**
 * Quyền (bảng permissions) — docs/05-user-roles-permissions.md.
 */
@Getter
@Setter
@Entity
@Table(name = "permissions")
public class Permission extends BaseAuditEntity {

    @Column(name = "code", nullable = false, unique = true, length = 100)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 255)
    private String description;
}
