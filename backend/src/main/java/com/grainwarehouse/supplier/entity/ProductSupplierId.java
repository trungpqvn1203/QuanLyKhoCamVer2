package com.grainwarehouse.supplier.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Khóa chính kép (supplier_id, product_id) của product_supplier.
 * JPA yêu cầu lớp khóa: Serializable, có constructor rỗng, equals/hashCode theo giá trị
 * (Hibernate dùng equals để tìm entity trong persistence context).
 */
@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ProductSupplierId implements Serializable {

    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "product_id")
    private Long productId;
}
