package com.example.pmdaily.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Đánh dấu method cần ghi audit tự động (cách 2 — hỗ trợ; docs/design/06 muc 3).
 * Cách chủ đạo vẫn là gọi AuditService.record() trực tiếp.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    String action();

    String entityType() default "";
}
