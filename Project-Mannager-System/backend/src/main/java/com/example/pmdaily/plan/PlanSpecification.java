package com.example.pmdaily.plan;

import java.util.Locale;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Subquery;

import com.example.pmdaily.project.ProjectMember;

public final class PlanSpecification {

    private PlanSpecification() {
    }

    public static Specification<ProjectPlan> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    public static Specification<ProjectPlan> keyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("planName")), like),
                cb.like(cb.lower(root.get("planCode")), like));
    }

    public static Specification<ProjectPlan> projectId(UUID projectId) {
        if (projectId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("project").get("id"), projectId);
    }

    public static Specification<ProjectPlan> planType(PlanType planType) {
        if (planType == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("planType"), planType);
    }

    public static Specification<ProjectPlan> status(PlanStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<ProjectPlan> memberOf(UUID userId) {
        return (root, query, cb) -> {
            Subquery<UUID> sub = query.subquery(UUID.class);
            var member = sub.from(ProjectMember.class);
            sub.select(member.get("project").get("id"))
                    .where(cb.equal(member.get("user").get("id"), userId));
            return cb.in(root.get("project").get("id")).value(sub);
        };
    }
}
