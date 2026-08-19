package com.example.pmdaily.audit;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

public final class AuditLogSpecification {

    private AuditLogSpecification() {
    }

    public static Specification<AuditLog> actorId(UUID actorId) {
        if (actorId == null) return null;
        return (root, query, cb) -> cb.equal(root.get("actorId"), actorId);
    }

    public static Specification<AuditLog> action(String action) {
        if (action == null || action.isBlank()) return null;
        return (root, query, cb) -> cb.equal(root.get("action"), action.trim());
    }

    public static Specification<AuditLog> entityType(String entityType) {
        if (entityType == null || entityType.isBlank()) return null;
        return (root, query, cb) -> cb.equal(root.get("entityType"), entityType.trim());
    }

    public static Specification<AuditLog> entityId(UUID entityId) {
        if (entityId == null) return null;
        return (root, query, cb) -> cb.equal(root.get("entityId"), entityId);
    }

    public static Specification<AuditLog> timeRange(Instant from, Instant to) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to != null) predicates.add(cb.lessThan(root.get("createdAt"), to));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }
}
