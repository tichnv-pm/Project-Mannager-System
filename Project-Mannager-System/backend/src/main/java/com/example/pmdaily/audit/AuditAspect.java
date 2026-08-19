package com.example.pmdaily.audit;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import com.example.pmdaily.common.BaseEntity;

/**
 * AOP ghi audit cho method @Audited (docs/design/06 muc 3 — cách 2).
 * entityId lấy từ kết quả trả về nếu là BaseEntity, ngược lại tham số đầu tiên là BaseEntity.
 */
@Aspect
@Component
public class AuditAspect {

    private final AuditService auditService;

    public AuditAspect(AuditService auditService) {
        this.auditService = auditService;
    }

    @Around("@annotation(com.example.pmdaily.audit.Audited)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Audited audited = method.getAnnotation(Audited.class);

        Object result = joinPoint.proceed();

        UUID entityId = extractEntityId(joinPoint.getArgs(), result);
        String entityType = audited.entityType().isBlank()
                ? defaultEntityType(result)
                : audited.entityType();
        Map<String, Object> after = result != null ? Map.of("entityId", entityId == null ? "" : entityId) : null;
        auditService.record(audited.action(), entityType, entityId, after);
        return result;
    }

    private UUID extractEntityId(Object[] args, Object result) {
        if (result instanceof BaseEntity baseEntity) {
            return baseEntity.getId();
        }
        for (Object arg : args) {
            if (arg instanceof BaseEntity baseEntity) {
                return baseEntity.getId();
            }
        }
        return null;
    }

    private String defaultEntityType(Object result) {
        if (result == null) {
            return "";
        }
        return result instanceof BaseEntity
                ? result.getClass().getSimpleName().toUpperCase()
                : result.getClass().getSimpleName().toUpperCase();
    }
}
