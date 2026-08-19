package com.example.pmdaily.task;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Subquery;

import com.example.pmdaily.project.ProjectMember;

/**
 * Specification tìm kiếm/lọc công việc (docs/api/05-task-api.md muc 3.1, FR-TASK-04).
 * Mọi truy vấn đều lọc deleted_at IS NULL (BR-TASK-09).
 */
public final class TaskSpecification {

    private TaskSpecification() {
    }

    public static Specification<Task> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    public static Specification<Task> keyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), like),
                cb.like(cb.lower(root.get("code")), like));
    }

    public static Specification<Task> projectId(UUID projectId) {
        if (projectId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("project").get("id"), projectId);
    }

    public static Specification<Task> assigneeId(UUID assigneeId) {
        if (assigneeId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("assignee").get("id"), assigneeId);
    }

    public static Specification<Task> statuses(List<TaskStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> root.get("status").in(statuses);
    }

    public static Specification<Task> priorities(List<TaskPriority> priorities) {
        if (priorities == null || priorities.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> root.get("priority").in(priorities);
    }

    public static Specification<Task> types(List<TaskType> types) {
        if (types == null || types.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> root.get("type").in(types);
    }

    public static Specification<Task> tagId(UUID tagId) {
        if (tagId == null) {
            return null;
        }
        return (root, query, cb) -> {
            Subquery<UUID> sub = query.subquery(UUID.class);
            var taskTag = sub.from(TaskTag.class);
            sub.select(taskTag.get("task").get("id"))
                    .where(cb.equal(taskTag.get("tag").get("id"), tagId));
            return cb.in(root.get("id")).value(sub);
        };
    }

    public static Specification<Task> startDateRange(LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("startDate"), to));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    public static Specification<Task> dueDateRange(LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dueDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dueDate"), to));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    public static Specification<Task> overdue(LocalDate today) {
        if (today == null) {
            return null;
        }
        return (root, query, cb) -> cb.and(
                cb.isNotNull(root.get("dueDate")),
                cb.lessThan(root.get("dueDate"), today),
                cb.not(root.get("status").in(TaskStatus.DONE, TaskStatus.CANCELLED)));
    }

    public static Specification<Task> blocked(boolean blocked) {
        return (root, query, cb) -> cb.equal(root.get("blocked"), blocked);
    }

    public static Specification<Task> memberOf(UUID userId) {
        return (root, query, cb) -> {
            Subquery<UUID> sub = query.subquery(UUID.class);
            var member = sub.from(ProjectMember.class);
            sub.select(member.get("project").get("id"))
                    .where(cb.equal(member.get("user").get("id"), userId));
            return cb.in(root.get("project").get("id")).value(sub);
        };
    }

    public static Specification<Task> sprintId(String sprintId) {
        if (sprintId == null) {
            return null;
        }
        if ("none".equalsIgnoreCase(sprintId) || "null".equalsIgnoreCase(sprintId) || sprintId.isBlank()) {
            return (root, query, cb) -> cb.isNull(root.get("sprintId"));
        }
        try {
            UUID sprintUuid = UUID.fromString(sprintId.trim());
            return (root, query, cb) -> cb.equal(root.get("sprintId"), sprintUuid);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
