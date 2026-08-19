package com.example.pmdaily.audit.mapper;

import org.mapstruct.Mapper;

import com.example.pmdaily.audit.AuditLog;
import com.example.pmdaily.audit.dto.AuditLogResponse;

@Mapper(componentModel = "spring")
public interface AuditLogMapper {
    AuditLogResponse toResponse(AuditLog auditLog);
}
