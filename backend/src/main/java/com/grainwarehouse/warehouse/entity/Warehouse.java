package com.grainwarehouse.warehouse.entity;

import com.grainwarehouse.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;

/** Kho hàng (BR-WH-01..04). */
@Getter
@Setter
@Entity
@Table(name = "warehouse")
public class Warehouse extends AuditableEntity {

    // DB tự sinh qua DEFAULT nextval(...) (BR-WH-02) — cùng cách với Product.code
    @Generated
    @Setter(AccessLevel.NONE)
    @Column(nullable = false, length = 20, insertable = false, updatable = false)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WarehouseType type;

    @Column(nullable = false, length = 500)
    private String address;

    @Column(nullable = false)
    private boolean active = true;
}
