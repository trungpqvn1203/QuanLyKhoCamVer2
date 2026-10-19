package com.grainwarehouse.inbound.dto;

import com.grainwarehouse.inventory.entity.StockDocumentItem;
import com.grainwarehouse.product.entity.Product;
import com.grainwarehouse.product.entity.ProductUnit;

import java.math.BigDecimal;

public record InboundReceiptLineResponse(
        Long id,
        Long productId,
        String productCode,
        String productName,
        ProductUnit unit,
        Integer orderedQuantity,
        int receivedQuantity,
        BigDecimal unitPrice,
        BigDecimal lineAmount) {

    public static InboundReceiptLineResponse from(StockDocumentItem item) {
        Product product = item.getProduct();
        return new InboundReceiptLineResponse(
                item.getId(),
                product.getId(),
                product.getCode(),
                product.getName(),
                product.getUnit(),
                item.getOrderedQuantity(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getLineAmount());
    }
}
