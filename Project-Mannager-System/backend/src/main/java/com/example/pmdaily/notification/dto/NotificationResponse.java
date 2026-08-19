package com.example.pmdaily.notification.dto;

import java.time.Instant;
import java.util.UUID;

import com.example.pmdaily.notification.NotificationType;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        String title,
        String content,
        String entityType,
        UUID entityId,
        boolean isRead,
        Instant readAt,
        Instant createdAt
) {}
