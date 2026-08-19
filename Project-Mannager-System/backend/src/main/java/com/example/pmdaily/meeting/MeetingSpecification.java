package com.example.pmdaily.meeting;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Subquery;

import com.example.pmdaily.project.ProjectMember;

/**
 * Specification tìm kiếm/lọc cuộc họp (docs/api/06-meeting-api.md muc 3.1, FR-MEET-04).
 * Mọi truy vấn đều lọc deleted_at IS NULL.
 */
public final class MeetingSpecification {

    private MeetingSpecification() {
    }

    public static Specification<Meeting> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    public static Specification<Meeting> keyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("title")), like);
    }

    public static Specification<Meeting> projectId(UUID projectId) {
        if (projectId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("project").get("id"), projectId);
    }

    public static Specification<Meeting> statuses(MeetingStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Meeting> timeRange(Instant from, Instant to) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startTime"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("startTime"), to));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    public static Specification<Meeting> memberOf(UUID userId) {
        return (root, query, cb) -> {
            Subquery<UUID> sub = query.subquery(UUID.class);
            var member = sub.from(ProjectMember.class);
            sub.select(member.get("project").get("id"))
                    .where(cb.equal(member.get("user").get("id"), userId));
            return cb.in(root.get("project").get("id")).value(sub);
        };
    }
}
