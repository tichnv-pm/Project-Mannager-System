package com.example.pmdaily.project;

import java.util.Locale;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;

/**
 * Specification tìm kiếm/lọc dự án (docs/api/04-project-api.md muc 3.1, FR-PROJ-04).
 * Mọi truy vấn đều lọc deleted_at IS NULL (BR-PROJ-07).
 */
public final class ProjectSpecification {

    private ProjectSpecification() {
    }

    public static Specification<Project> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    public static Specification<Project> keyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("code")), like),
                cb.like(cb.lower(root.get("name")), like),
                cb.like(cb.lower(root.get("customerName")), like));
    }

    public static Specification<Project> status(ProjectStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Project> projectManager(UUID projectManagerId) {
        if (projectManagerId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("projectManager").get("id"), projectManagerId);
    }

    public static Specification<Project> memberOf(UUID userId) {
        return (root, query, cb) -> {
            Join<Project, ProjectMember> members = root.join("members", JoinType.INNER);
            return cb.equal(members.get("user").get("id"), userId);
        };
    }
}
