package com.grainwarehouse.product.entity;

import com.grainwarehouse.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Đơn vị giao dịch của sản phẩm: Bao, Kg, Túi, Thùng... (BR-PRD-05, BR-UNIT-01..04).
 * Mỗi SP một đơn vị, không quy đổi. Tấn không phải đơn vị giao dịch — chỉ quy đổi khi báo cáo (BR-PRD-06).
 */
@Getter
@Setter
@Entity
@Table(name = "product_unit")
public class ProductUnit extends AuditableEntity {

    @Column(nullable = false, length = 50)
    private String name;

    // BR-UNIT-03: chỉ dòng hệ thống (Kg = 1) có giá trị, người dùng không đặt được → không có setter
    @Setter(AccessLevel.NONE)
    @Column(name = "fixed_net_weight_kg", precision = 10, scale = 3, updatable = false, insertable = false)
    private BigDecimal fixedNetWeightKg;

    @Column(nullable = false)
    private boolean active = true;
}
