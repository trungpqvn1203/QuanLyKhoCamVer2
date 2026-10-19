package com.grainwarehouse.supplier.dto;

import com.grainwarehouse.product.entity.Product;
import com.grainwarehouse.product.entity.ProductUnit;
import com.grainwarehouse.supplier.entity.ProductSupplier;

/** Một SP đang liên kết với NCC. productActive cho biết SP còn kinh doanh hay không. */
public record SupplierProductResponse(
        Long productId,
        String productCode,
        String productName,
        ProductUnit unit,
        boolean productActive) {

    public static SupplierProductResponse from(ProductSupplier link) {
        Product product = link.getProduct();
        return new SupplierProductResponse(
                product.getId(),
                product.getCode(),
                product.getName(),
                product.getUnit(),
                product.isActive());
    }
}
