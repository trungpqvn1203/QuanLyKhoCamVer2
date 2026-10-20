package com.grainwarehouse.sales.dto;

import com.grainwarehouse.inventory.entity.StockDocumentItem;
import com.grainwarehouse.product.entity.Product;
import com.grainwarehouse.product.entity.ProductUnit;

import java.math.BigDecimal;

public record SalesOrderLineResponse(
        Long id,
        Long productId,
        String productCode,
        String productName,
        ProductUnit unit,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineAmount) {

    public static SalesOrderLineResponse from(StockDocumentItem item) {
        Product product = item.getProduct();
        return new SalesOrderLineResponse(
                item.getId(),
                product.getId(),
                product.getCode(),
                product.getName(),
                product.getUnit(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getLineAmount());
    }
}
