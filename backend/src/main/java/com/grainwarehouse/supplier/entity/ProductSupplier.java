package com.grainwarehouse.supplier.entity;

import com.grainwarehouse.product.entity.Product;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Liên kết NCC – SP (BR-SUP-05): NCC nào được phép cung cấp SP nào.
 * Không kế thừa BaseEntity vì không có cột id riêng — khóa chính là cặp (supplier_id, product_id).
 */
@Getter
@Entity
@Table(name = "product_supplier")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductSupplier {

    @EmbeddedId
    private ProductSupplierId id;

    // @MapsId: cột product_id vừa là một phần khóa chính vừa là FK tới product.
    // Map quan hệ để trả về mã/tên SP mà không cần query riêng.
    @MapsId("productId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    // Gỡ liên kết = false, gán lại = true trên chính dòng này
    @Setter
    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ProductSupplier(Long supplierId, Product product) {
        this.id = new ProductSupplierId(supplierId, product.getId());
        this.product = product;
    }
}
