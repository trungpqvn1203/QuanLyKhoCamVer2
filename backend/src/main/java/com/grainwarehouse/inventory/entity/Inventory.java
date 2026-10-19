package com.grainwarehouse.inventory.entity;

import com.grainwarehouse.common.entity.AuditableEntity;
import com.grainwarehouse.product.entity.Product;
import com.grainwarehouse.warehouse.entity.Warehouse;
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

/**
 * Tồn hiện tại của một SP tại một kho (snapshot). Qua JPA chỉ để ĐỌC (@Immutable, không setter):
 * mọi thay đổi số lượng đi qua câu SQL nguyên tử trong InventoryRepository (cộng bằng ON CONFLICT,
 * trừ bằng conditional update), luôn kèm một StockTransaction (BR-INV-04).
 */
@Getter
@Entity
@Immutable
@Table(name = "inventory")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inventory extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(nullable = false)
    private int quantity;
}
