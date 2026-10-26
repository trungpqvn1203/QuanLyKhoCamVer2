package com.grainwarehouse.cashbook.entity;

import com.grainwarehouse.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * Danh mục thu/chi khác (BR-CASH-05): "Tiền điện", "Xăng xe", "Chủ góp vốn"... Chiều (thu/chi) cố định từ lúc tạo —
 * đổi chiều sẽ làm phiếu cũ của danh mục đó mang nghĩa ngược lại. Không xóa cứng: ngừng dùng bằng active = false.
 */
@Getter
@Setter
@Entity
@Table(name = "cash_category")
public class CashCategory extends AuditableEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3, updatable = false)
    private CashDirection direction;

    @Column(nullable = false)
    private boolean active = true;

    protected CashCategory() {
    }

    public CashCategory(String name, CashDirection direction) {
        this.name = name;
        this.direction = direction;
    }
}
