package com.grainwarehouse.delivery.entity;

import com.grainwarehouse.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Xe giao hàng (BR-VEH-01..04). Không xóa cứng — ngừng dùng bằng active = false. */
@Getter
@Setter
@Entity
@Table(name = "vehicle")
public class Vehicle extends AuditableEntity {

    // BR-VEH-01: lưu như người dùng gõ (đã viết hoa); trùng xét theo dạng bỏ khoảng trắng / chấm / gạch (uk_vehicle_plate)
    @Column(name = "plate_number", nullable = false, length = 20)
    private String plateNumber;

    @Column(length = 100)
    private String name;

    // BR-VEH-02: null = không cảnh báo quá tải
    @Column(name = "capacity_tons", precision = 6, scale = 3)
    private BigDecimal capacityTons;

    @Column(nullable = false)
    private boolean active = true;
}
