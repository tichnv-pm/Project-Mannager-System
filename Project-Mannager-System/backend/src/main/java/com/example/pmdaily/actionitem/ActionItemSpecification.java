package com.example.pmdaily.actionitem;

import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Subquery;

import com.example.pmdaily.project.ProjectMember;

/**
 * Specification tìm kiếm/lọc action item (docs/api/07-action-item-api.md muc 3.1, FR-AI-04).
 * Mọi truy vấn đều lọc deleted_at IS NULL.
 */
public final class ActionItemSpecification {

    private ActionItemSpecification() {
    }

    public static Specification<ActionItem> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    public static Specification<ActionItem> keyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("title")), like);
    }

    public static Specification<ActionItem> projectId(UUID projectId) {
        if (projectId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("project").get("id"), projectId);
    }

    public static Specification<ActionItem> meetingId(UUID meetingId) {
        if (meetingId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("meeting").get("id"), meetingId);
    }

    public static Specification<ActionItem> statuses(ActionItemStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<ActionItem> assigneeId(UUID assigneeId) {
        if (assigneeId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("assignee").get("id"), assigneeId);
    }

    public static Specification<ActionItem> overdue(LocalDate today) {
        if (today == null) {
            return null;
        }
        return (root, query, cb) -> cb.and(
                cb.isNotNull(root.get("dueDate")),
                cb.lessThan(root.get("dueDate"), today),
                cb.not(root.get("status").in(ActionItemStatus.DONE, ActionItemStatus.CANCELLED)));
    }

    public static Specification<ActionItem> memberOf(UUID userId) {
        return (root, query, cb) -> {
            Subquery<UUID> sub = query.subquery(UUID.class);
            var member = sub.from(ProjectMember.class);
            sub.select(member.get("project").get("id"))
                    .where(cb.equal(member.get("user").get("id"), userId));
            return cb.in(root.get("project").get("id")).value(sub);
        };
    }
}
