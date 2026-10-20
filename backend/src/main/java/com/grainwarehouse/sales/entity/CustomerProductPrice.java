package com.grainwarehouse.sales.entity;

import com.grainwarehouse.common.entity.AuditableEntity;
import com.grainwarehouse.customer.entity.Customer;
import com.grainwarehouse.product.entity.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Giá bán riêng hiện hành của một SP cho một khách (BR-SAL-06). Chỉ dùng để điền sẵn giá khi lập
 * phiếu; không lưu lịch sử vì giá thực bán đã chụp lại trên từng dòng phiếu.
 * Ghi qua CustomerProductPriceRepository.upsert (một câu SQL) — entity chỉ để đọc.
 */
@Getter
@Entity
@Table(name = "customer_product_price")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerProductPrice extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;
}
