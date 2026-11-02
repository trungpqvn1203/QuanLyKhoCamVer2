package com.grainwarehouse.sales.entity;

import com.grainwarehouse.common.entity.BaseEntity;
import com.grainwarehouse.inventory.entity.StockDocument;
import com.grainwarehouse.inventory.entity.StockDocumentItem;
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
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;

/**
 * Một dòng phiếu đổi giá trong một lần điều chỉnh (BR-SAL-23..26) — BẤT BIẾN, cùng ba lớp bảo vệ như
 * SalePayment: không setter, @Immutable, trigger trg_sale_price_adjustment_immutable ở DB.
 * Dòng phiếu giữ giá hiện hành; bảng này giữ "giá cũ → giá mới, ai sửa, vì sao".
 */
@Getter
@Entity
@Immutable
@Table(name = "sale_price_adjustment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SalePriceAdjustment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private StockDocument document;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_item_id", nullable = false)
    private StockDocumentItem documentItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "old_unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal oldUnitPrice;

    @Column(name = "new_unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal newUnitPrice;

    @Column(name = "old_line_amount", nullable = false, precision = 18, scale = 0)
    private BigDecimal oldLineAmount;

    @Column(name = "new_line_amount", nullable = false, precision = 18, scale = 0)
    private BigDecimal newLineAmount;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "adjusted_by", nullable = false, length = 50)
    private String adjustedBy;

    /** Ghi lại một dòng VỪA đổi giá: giá / thành tiền cũ truyền vào, giá / thành tiền mới đọc từ dòng. */
    public static SalePriceAdjustment of(StockDocumentItem item, BigDecimal oldUnitPrice, BigDecimal oldLineAmount,
                                         String reason, String adjustedBy) {
        SalePriceAdjustment adjustment = new SalePriceAdjustment();
        adjustment.document = item.getDocument();
        adjustment.documentItem = item;
        adjustment.product = item.getProduct();
        adjustment.oldUnitPrice = oldUnitPrice;
        adjustment.oldLineAmount = oldLineAmount;
        adjustment.newUnitPrice = item.getUnitPrice();
        adjustment.newLineAmount = item.getLineAmount();
        adjustment.reason = reason;
        adjustment.adjustedBy = adjustedBy;
        return adjustment;
    }
}
