package com.grainwarehouse.sales.dto;

import com.grainwarehouse.inventory.dto.StockFigures;
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
        BigDecimal lineAmount,
        // Khối lượng dòng (tấn, 3 chữ số lẻ) — in phiếu xuất kho cho lái xe biết xe chở bao nhiêu (BR-PRT-03)
        BigDecimal tons) {

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
                item.getLineAmount(),
                StockFigures.tons(item.getQuantity(), product.getNetWeightKg()));
    }
}
