package com.grainwarehouse.pricelist.entity;

import com.grainwarehouse.common.entity.AuditableEntity;
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
 * Giá hiện hành của một SP trong một bảng giá (BR-PRC-02). Không lưu lịch sử: giá thực bán đã chụp trên dòng phiếu.
 * Ghi qua PriceListItemRepository.upsert (một câu SQL) — entity chỉ để đọc.
 */
@Getter
@Entity
@Table(name = "price_list_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PriceListItem extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "price_list_id", nullable = false, updatable = false)
    private PriceList priceList;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;
}
