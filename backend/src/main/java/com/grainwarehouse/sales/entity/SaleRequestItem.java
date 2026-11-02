package com.grainwarehouse.sales.entity;

import com.grainwarehouse.common.entity.AuditableEntity;
import com.grainwarehouse.inventory.entity.StockDocument;
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
import lombok.Setter;

/**
 * Một dòng "khách đặt" của phiếu bán (BR-SAL-17, 20): SP + SL theo đơn vị tính của SP, chưa có giá.
 * Sửa được khi phiếu Chờ xác nhận; từ lúc xác nhận chỉ còn để đối chiếu với dòng phiếu thực giao.
 * Không nằm trong StockDocument.items: dòng phiếu là phần kho chốt (có giá, tác động tồn), còn bảng này
 * giữ yêu cầu gốc — kể cả SP mà kho đã đổi sang loại khác khi xác nhận.
 */
@Getter
@Entity
@Table(name = "sale_request_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SaleRequestItem extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false, updatable = false)
    private StockDocument document;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @Setter
    @Column(nullable = false)
    private int quantity;

    public SaleRequestItem(StockDocument document, Product product, int quantity) {
        this.document = document;
        this.product = product;
        this.quantity = quantity;
    }
}
