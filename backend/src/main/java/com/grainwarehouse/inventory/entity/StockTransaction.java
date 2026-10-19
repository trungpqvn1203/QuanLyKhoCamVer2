package com.grainwarehouse.inventory.entity;

import com.grainwarehouse.common.entity.BaseEntity;
import com.grainwarehouse.product.entity.Product;
import com.grainwarehouse.warehouse.entity.Warehouse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * Một biến động tồn của một SP tại một kho — BẤT BIẾN (BR-INV-05).
 * Ba lớp bảo vệ: không có setter; @Immutable (Hibernate bỏ qua mọi thay đổi, không sinh UPDATE);
 * trigger trg_stock_transaction_immutable ở DB chặn UPDATE/DELETE dù đến từ đâu.
 * Kế thừa BaseEntity (không có updated_at) vì bản ghi không bao giờ được cập nhật.
 */
@Getter
@Entity
@Immutable
@Table(name = "stock_transaction")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockTransaction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_item_id", nullable = false)
    private StockDocumentItem documentItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StockTransactionType type;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "quantity_before", nullable = false)
    private int quantityBefore;

    @Column(name = "quantity_after", nullable = false)
    private int quantityAfter;

    // BR-INV-06: giao dịch đảo trỏ về giao dịch gốc; NULL với giao dịch thường
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversal_of_id")
    private StockTransaction reversalOf;

    public StockTransaction(StockDocumentItem documentItem, Warehouse warehouse, StockTransactionType type,
                            int quantity, int quantityBefore, int quantityAfter, StockTransaction reversalOf) {
        this.documentItem = documentItem;
        this.product = documentItem.getProduct();
        this.warehouse = warehouse;
        this.type = type;
        this.quantity = quantity;
        this.quantityBefore = quantityBefore;
        this.quantityAfter = quantityAfter;
        this.reversalOf = reversalOf;
    }
}
