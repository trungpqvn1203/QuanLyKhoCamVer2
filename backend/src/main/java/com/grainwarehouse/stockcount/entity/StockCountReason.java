package com.grainwarehouse.stockcount.entity;

import com.grainwarehouse.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Lý do lệch khi kiểm kê (BR-CNT-05): "Hao hụt tự nhiên", "Hư hỏng, ẩm mốc"... do người dùng tự quản lý như danh mục
 * thu/chi. Không xóa cứng: ngừng dùng bằng active = false (dòng kiểm kê cũ vẫn trỏ tới được).
 */
@Getter
@Setter
@Entity
@Table(name = "stock_count_reason")
public class StockCountReason extends AuditableEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    protected StockCountReason() {
    }

    public StockCountReason(String name) {
        this.name = name;
    }
}
