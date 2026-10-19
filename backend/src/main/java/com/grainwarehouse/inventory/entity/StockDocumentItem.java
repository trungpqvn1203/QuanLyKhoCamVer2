package com.grainwarehouse.inventory.entity;

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
import java.math.RoundingMode;

/** Dòng chứng từ: một SP, số lượng, đơn giá, thành tiền. */
@Getter
@Entity
@Table(name = "stock_document_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockDocumentItem extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false, updatable = false)
    private StockDocument document;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    // BR-IMP-03: SL đặt — chỉ phiếu nhập dùng, các loại phiếu khác để NULL
    @Column(name = "ordered_quantity")
    private Integer orderedQuantity;

    // SL thực tế tác động tồn (phiếu nhập: SL thực nhận). Số nguyên (BR-INV-07)
    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "line_amount", nullable = false, precision = 18, scale = 0)
    private BigDecimal lineAmount;

    public StockDocumentItem(Product product) {
        this.product = product;
    }

    void attachTo(StockDocument document) {
        this.document = document;
    }

    /** Ghi số lượng, đơn giá và tính lại thành tiền — không có cách nào đổi giá mà quên tính tiền. */
    public void setValues(Integer orderedQuantity, int quantity, BigDecimal unitPrice) {
        this.orderedQuantity = orderedQuantity;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.lineAmount = lineAmount(quantity, unitPrice);
    }

    /**
     * BR-MON-05: thành tiền = số lượng × đơn giá, làm tròn HALF_UP về đồng.
     * Ví dụ 3 bao × 12.345,50 đ = 37.036,50 → 37.037 đ.
     */
    public static BigDecimal lineAmount(int quantity, BigDecimal unitPrice) {
        return unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(0, RoundingMode.HALF_UP);
    }
}
