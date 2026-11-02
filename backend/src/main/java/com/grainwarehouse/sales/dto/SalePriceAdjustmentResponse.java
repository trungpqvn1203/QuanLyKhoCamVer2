package com.grainwarehouse.sales.dto;

import com.grainwarehouse.product.entity.Product;
import com.grainwarehouse.sales.entity.SalePriceAdjustment;

import java.math.BigDecimal;
import java.time.Instant;

/** Một dòng lịch sử điều chỉnh giá (BR-SAL-26). Các dòng cùng createdAt + reason là cùng một lần điều chỉnh. */
public record SalePriceAdjustmentResponse(
        Long id,
        Long productId,
        String productCode,
        String productName,
        BigDecimal oldUnitPrice,
        BigDecimal newUnitPrice,
        BigDecimal oldLineAmount,
        BigDecimal newLineAmount,
        String reason,
        String adjustedBy,
        Instant createdAt) {

    public static SalePriceAdjustmentResponse from(SalePriceAdjustment adjustment) {
        Product product = adjustment.getProduct();
        return new SalePriceAdjustmentResponse(
                adjustment.getId(),
                product.getId(),
                product.getCode(),
                product.getName(),
                adjustment.getOldUnitPrice(),
                adjustment.getNewUnitPrice(),
                adjustment.getOldLineAmount(),
                adjustment.getNewLineAmount(),
                adjustment.getReason(),
                adjustment.getAdjustedBy(),
                adjustment.getCreatedAt());
    }
}
