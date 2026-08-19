package com.example.pmdaily.notification;

import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

public final class NotificationSpecification {

    private NotificationSpecification() {
    }

    public static Specification<Notification> recipientId(UUID recipientId) {
        if (recipientId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("recipient").get("id"), recipientId);
    }

    public static Specification<Notification> unreadOnly(Boolean unreadOnly) {
        if (unreadOnly == null || !unreadOnly) {
            return null;
        }
        return (root, query, cb) -> cb.isFalse(root.get("isRead"));
    }

    public static Specification<Notification> type(NotificationType type) {
        if (type == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("type"), type);
    }
}
