package com.example.pmdaily.common;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * Entity có soft delete (deletedAt/deletedBy) cho dữ liệu nghiệp vụ.
 * Mọi truy vấn mặc định phải lọc {@code deleted_at IS NULL} (docs/design/02 muc 3):
 * khuyến nghị dùng @SQLRestriction/@Where trên entity con khi cần, thống nhất một cách duy nhất.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class SoftDeleteEntity extends BaseAuditEntity {

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
