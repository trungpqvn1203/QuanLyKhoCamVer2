package com.grainwarehouse.stockcount.dto;

import com.grainwarehouse.inventory.entity.StockDocumentItem;
import com.grainwarehouse.product.entity.Product;
import com.grainwarehouse.product.entity.ProductUnit;

/** Một dòng kiểm kê. countedQuantity / difference không có = chưa đếm. difference dương = thừa, âm = thiếu. */
public record StockCountLineResponse(
        Long id,
        Long productId,
        String productCode,
        String productName,
        ProductUnit unit,
        boolean productActive,
        int bookQuantity,
        Integer countedQuantity,
        Integer difference,
        Long reasonId,
        String reasonName,
        String reasonNote) {

    public static StockCountLineResponse from(StockDocumentItem item) {
        Product product = item.getProduct();
        return new StockCountLineResponse(
                item.getId(),
                product.getId(),
                product.getCode(),
                product.getName(),
                product.getUnit(),
                product.isActive(),
                item.getBookQuantity(),
                item.getCountedQuantity(),
                item.getDifference(),
                item.getReason() == null ? null : item.getReason().getId(),
                item.getReason() == null ? null : item.getReason().getName(),
                item.getReasonNote());
    }
}
