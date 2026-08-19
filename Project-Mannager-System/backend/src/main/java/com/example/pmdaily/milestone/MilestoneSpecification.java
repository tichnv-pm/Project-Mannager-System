package com.example.pmdaily.milestone;

import java.util.Locale;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Subquery;

import com.example.pmdaily.project.ProjectMember;

public final class MilestoneSpecification {

    private MilestoneSpecification() {
    }

    public static Specification<Milestone> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    public static Specification<Milestone> keyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), like);
    }

    public static Specification<Milestone> projectId(UUID projectId) {
        if (projectId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("project").get("id"), projectId);
    }

    public static Specification<Milestone> status(MilestoneStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Milestone> memberOf(UUID userId) {
        return (root, query, cb) -> {
            Subquery<UUID> sub = query.subquery(UUID.class);
            var member = sub.from(ProjectMember.class);
            sub.select(member.get("project").get("id"))
                    .where(cb.equal(member.get("user").get("id"), userId));
            return cb.in(root.get("project").get("id")).value(sub);
        };
    }
}
