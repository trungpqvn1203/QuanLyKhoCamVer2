package com.grainwarehouse.transfer.dto;

import com.grainwarehouse.inventory.entity.StockDocumentItem;
import com.grainwarehouse.product.entity.Product;

import java.math.BigDecimal;

/** netWeightKg để giao diện quy ra tấn (BR-STK-02) — xe chở theo tấn, không theo bao. */
public record TransferLineResponse(
        Long id,
        Long productId,
        String productCode,
        String productName,
        String unitName,
        BigDecimal netWeightKg,
        int quantity) {

    public static TransferLineResponse from(StockDocumentItem item) {
        Product product = item.getProduct();
        return new TransferLineResponse(
                item.getId(),
                product.getId(),
                product.getCode(),
                product.getName(),
                product.getUnit().getName(),
                product.getNetWeightKg(),
                item.getQuantity());
    }
}
