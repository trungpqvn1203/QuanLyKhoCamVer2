package com.grainwarehouse.sales.dto;

import com.grainwarehouse.product.entity.Product;
import com.grainwarehouse.product.entity.ProductUnit;
import com.grainwarehouse.sales.entity.CustomerProductPrice;

import java.math.BigDecimal;
import java.time.Instant;

public record CustomerPriceResponse(
        Long productId,
        String productCode,
        String productName,
        ProductUnit unit,
        boolean productActive,
        BigDecimal unitPrice,
        Instant updatedAt) {

    public static CustomerPriceResponse from(CustomerProductPrice price) {
        Product product = price.getProduct();
        return new CustomerPriceResponse(
                product.getId(),
                product.getCode(),
                product.getName(),
                product.getUnit(),
                product.isActive(),
                price.getUnitPrice(),
                price.getUpdatedAt());
    }
}
