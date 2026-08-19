package com.example.pmdaily.notification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.pmdaily.common.PageResponse;
import com.example.pmdaily.exception.ResourceNotFoundException;
import com.example.pmdaily.notification.dto.NotificationResponse;
import com.example.pmdaily.notification.dto.ReadAllResponse;
import com.example.pmdaily.notification.dto.UnreadCountResponse;
import com.example.pmdaily.notification.mapper.NotificationMapper;
import com.example.pmdaily.security.UserPrincipal;
import com.example.pmdaily.user.User;

@Service
@Transactional(readOnly = true)
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;

    public NotificationService(
            NotificationRepository notificationRepository,
            NotificationMapper notificationMapper) {
        this.notificationRepository = notificationRepository;
        this.notificationMapper = notificationMapper;
    }

    public PageResponse<NotificationResponse> getUserNotifications(
            UserPrincipal actor,
            Boolean unreadOnly,
            NotificationType type,
            int page,
            int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Specification<Notification> spec = Specification.where(NotificationSpecification.recipientId(actor.getId()))
                .and(NotificationSpecification.unreadOnly(unreadOnly))
                .and(NotificationSpecification.type(type));

        var notificationPage = notificationRepository.findAll(spec, pageable);
        return PageResponse.of(notificationPage, notificationMapper::toResponse);
    }

    public UnreadCountResponse getUnreadCount(UserPrincipal actor) {
        long count = notificationRepository.countByRecipient_IdAndIsReadFalse(actor.getId());
        return new UnreadCountResponse(count);
    }

    @Transactional
    public NotificationResponse markAsRead(UserPrincipal actor, UUID id) {
        Notification notification = notificationRepository.findById(id)
                .filter(n -> Objects.equals(n.getRecipient().getId(), actor.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Notification", id));

        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(Instant.now());
            notification = notificationRepository.save(notification);
            log.info("notification.read success id={} user={}", id, actor.getUsername());
        }

        return notificationMapper.toResponse(notification);
    }

    @Transactional
    public ReadAllResponse markAllAsRead(UserPrincipal actor) {
        int updated = notificationRepository.markAllAsReadByRecipientId(actor.getId(), Instant.now());
        log.info("notification.read_all success user={} count={}", actor.getUsername(), updated);
        return new ReadAllResponse(updated);
    }

    @Transactional
    public Notification createNotificationInternal(
            User recipient,
            NotificationType type,
            String title,
            String content,
            String entityType,
            UUID entityId) {
        if (recipient == null) {
            return null;
        }

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant startOfDay = today.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant endOfDay = today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        if (entityId != null && notificationRepository.existsByRecipient_IdAndTypeAndEntityIdAndCreatedAtBetween(
                recipient.getId(), type, entityId, startOfDay, endOfDay)) {
            log.debug("notification.deduplicate skipped recipient={} type={} entityId={}",
                    recipient.getId(), type, entityId);
            return null;
        }

        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setEntityType(entityType);
        notification.setEntityId(entityId);
        notification.setRead(false);
        notification.setCreatedAt(Instant.now());

        Notification saved = notificationRepository.save(notification);
        log.info("notification.created recipient={} type={} entityId={}", recipient.getUsername(), type, entityId);
        return saved;
    }
}
