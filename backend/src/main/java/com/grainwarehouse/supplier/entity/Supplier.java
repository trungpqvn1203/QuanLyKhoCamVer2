package com.grainwarehouse.supplier.entity;

import com.grainwarehouse.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;

/** Nhà cung cấp (BR-SUP-01..04, BR-SUP-09). */
@Getter
@Setter
@Entity
@Table(name = "supplier")
public class Supplier extends AuditableEntity {

    // DB tự sinh qua DEFAULT nextval(...) (BR-SUP-02) — cùng cách với Product.code, Warehouse.code
    @Generated
    @Setter(AccessLevel.NONE)
    @Column(nullable = false, length = 20, insertable = false, updatable = false)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    // BR-SUP-09: tùy chọn; có thì đã chuẩn hóa, đúng định dạng (ck_supplier_tax_code) và duy nhất (uk_supplier_tax_code)
    @Column(name = "tax_code", length = 14)
    private String taxCode;

    @Column(length = 20)
    private String phone;

    @Column(length = 254)
    private String email;

    @Column(length = 500)
    private String address;

    @Column(nullable = false)
    private boolean active = true;
}
