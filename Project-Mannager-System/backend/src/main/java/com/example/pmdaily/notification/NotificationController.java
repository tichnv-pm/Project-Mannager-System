package com.example.pmdaily.notification;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.pmdaily.common.PageResponse;
import com.example.pmdaily.notification.dto.NotificationResponse;
import com.example.pmdaily.notification.dto.ReadAllResponse;
import com.example.pmdaily.notification.dto.UnreadCountResponse;
import com.example.pmdaily.security.UserPrincipal;

/**
 * REST API Notification (docs/api/11-notification-api.md) — 4 endpoints.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('notification:view')")
    public PageResponse<NotificationResponse> list(
            @AuthenticationPrincipal UserPrincipal actor,
            @RequestParam(defaultValue = "false") Boolean unreadOnly,
            @RequestParam(required = false) NotificationType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return notificationService.getUserNotifications(actor, unreadOnly, type, page, size);
    }

    @GetMapping("/unread-count")
    @PreAuthorize("hasAuthority('notification:view')")
    public UnreadCountResponse unreadCount(@AuthenticationPrincipal UserPrincipal actor) {
        return notificationService.getUnreadCount(actor);
    }

    @PutMapping("/{id}/read")
    @PreAuthorize("hasAuthority('notification:manage')")
    public NotificationResponse markAsRead(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable UUID id) {
        return notificationService.markAsRead(actor, id);
    }

    @PutMapping("/read-all")
    @PreAuthorize("hasAuthority('notification:manage')")
    public ReadAllResponse readAll(@AuthenticationPrincipal UserPrincipal actor) {
        return notificationService.markAllAsRead(actor);
    }
}
