package com.grainwarehouse.sales.dto;

import com.grainwarehouse.inventory.entity.StockDocumentItem;
import com.grainwarehouse.product.entity.Product;

import java.math.BigDecimal;

public record SalesOrderLineResponse(
        Long id,
        Long productId,
        String productCode,
        String productName,
        String unitName,
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
                product.getUnit().getName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getLineAmount());
    }
}
