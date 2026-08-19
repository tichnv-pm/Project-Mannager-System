package com.example.pmdaily.audit;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.pmdaily.common.Constants;
import com.example.pmdaily.security.UserPrincipal;

/**
 * Ghi audit log — cách chủ đạo: service nghiệp vụ gọi record() ngay trong transaction
 * (docs/design/06-logging-audit-design.md muc 3). Không dùng transaction riêng:
 * audit ghi cùng transaction nghiệp vụ, thất bại nghiệp vụ → không để lại audit sai lệch.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;
    private final AuditDataSanitizer sanitizer;

    public AuditService(AuditLogRepository auditLogRepository, AuditDataSanitizer sanitizer) {
        this.auditLogRepository = auditLogRepository;
        this.sanitizer = sanitizer;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(String action, String entityType, UUID entityId,
            Map<String, Object> beforeData, Map<String, Object> afterData) {
        AuditLog entry = new AuditLog();
        entry.setId(UUID.randomUUID());
        entry.setTraceId(MDC.get(Constants.MDC_TRACE_ID));
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setBeforeData(sanitizer.sanitize(beforeData));
        entry.setAfterData(sanitizer.sanitize(afterData));

        UserPrincipal actor = currentActor();
        if (actor != null) {
            entry.setActorId(actor.getId());
            entry.setActorUsername(actor.getUsername());
        }
        auditLogRepository.save(entry);
        log.info("audit action={} entityType={} entityId={} actorId={}",
                action, entityType, entityId, entry.getActorId());
    }

    public void record(String action, String entityType, UUID entityId, Map<String, Object> afterData) {
        record(action, entityType, entityId, null, afterData);
    }

    private UserPrincipal currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return null;
        }
        return principal;
    }
}
