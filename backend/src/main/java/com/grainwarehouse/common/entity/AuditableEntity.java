package com.grainwarehouse.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Entity có thể cập nhật: thêm updatedAt.
 * Lưu ý: @UpdateTimestamp chỉ chạy khi UPDATE đi qua Hibernate (lúc flush).
 * Câu UPDATE viết tay (ví dụ trừ tồn kho) phải tự set updated_at = now().
 */
@Getter
@MappedSuperclass
public abstract class AuditableEntity extends BaseEntity {

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
