package com.grainwarehouse.delivery.dto;

import com.grainwarehouse.inventory.dto.StockFigures;
import com.grainwarehouse.inventory.entity.StockDocumentItem;
import com.grainwarehouse.product.entity.Product;

import java.math.BigDecimal;

/** Một dòng hàng cần giao — không có giá (phiếu chuyến không in giá, BR-DLV-08). */
public record DeliveryOrderLineResponse(
        Long productId,
        String productCode,
        String productName,
        String unitName,
        int quantity,
        BigDecimal tons) {

    public static DeliveryOrderLineResponse from(StockDocumentItem item) {
        Product product = item.getProduct();
        return new DeliveryOrderLineResponse(product.getId(), product.getCode(), product.getName(),
                product.getUnit().getName(), item.getQuantity(),
                StockFigures.tons(item.getQuantity(), product.getNetWeightKg()));
    }
}
