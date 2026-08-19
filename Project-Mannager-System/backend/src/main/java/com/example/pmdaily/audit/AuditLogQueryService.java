package com.example.pmdaily.audit;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.pmdaily.audit.dto.AuditLogResponse;
import com.example.pmdaily.audit.mapper.AuditLogMapper;
import com.example.pmdaily.common.PageResponse;

@Service
@Transactional(readOnly = true)
public class AuditLogQueryService {

    private final AuditLogRepository auditLogRepository;
    private final AuditLogMapper auditLogMapper;

    public AuditLogQueryService(AuditLogRepository auditLogRepository, AuditLogMapper auditLogMapper) {
        this.auditLogRepository = auditLogRepository;
        this.auditLogMapper = auditLogMapper;
    }

    public PageResponse<AuditLogResponse> search(
            UUID actorId,
            String action,
            String entityType,
            UUID entityId,
            Instant fromDate,
            Instant toDate,
            int page,
            int size,
            String sortStr) {
        Pageable pageable = createPageable(page, size, sortStr);
        Specification<AuditLog> spec = Specification.where(AuditLogSpecification.actorId(actorId))
                .and(AuditLogSpecification.action(action))
                .and(AuditLogSpecification.entityType(entityType))
                .and(AuditLogSpecification.entityId(entityId))
                .and(AuditLogSpecification.timeRange(fromDate, toDate));

        var logPage = auditLogRepository.findAll(spec, pageable);
        return PageResponse.of(logPage, auditLogMapper::toResponse);
    }

    private Pageable createPageable(int page, int size, String sortStr) {
        if (sortStr == null || sortStr.isBlank()) {
            return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        }
        String[] parts = sortStr.split(",");
        String property = parts[0].trim();
        Sort.Direction direction = (parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim()))
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(page, size, Sort.by(direction, property));
    }
}
