package com.grainwarehouse.supplier.repository;

import com.grainwarehouse.supplier.entity.ProductSupplier;
import com.grainwarehouse.supplier.entity.ProductSupplierId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductSupplierRepository extends JpaRepository<ProductSupplier, ProductSupplierId> {

    // "IdSupplierId" = thuộc tính id.supplierId. @EntityGraph: JOIN lấy luôn product, tránh N+1
    @EntityGraph(attributePaths = "product")
    Page<ProductSupplier> findByIdSupplierIdAndActiveTrue(Long supplierId, Pageable pageable);

    // Chiều ngược: SP này do những NCC nào cung cấp — dùng idx_product_supplier_product
    @EntityGraph(attributePaths = "supplier")
    Page<ProductSupplier> findByIdProductIdAndActiveTrue(Long productId, Pageable pageable);
}
