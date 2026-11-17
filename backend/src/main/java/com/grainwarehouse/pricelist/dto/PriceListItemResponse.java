package com.grainwarehouse.pricelist.dto;

import com.grainwarehouse.pricelist.entity.PriceListItem;
import com.grainwarehouse.product.entity.Product;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Một giá trong bảng giá. Thông tin SP chỉ có khi đọc theo một bảng giá (GET /{id}/items);
 * đọc theo nhóm SP (màn ma trận) chỉ cần priceListId + productId + unitPrice (null bị bỏ khỏi JSON).
 */
public record PriceListItemResponse(
        Long priceListId,
        Long productId,
        String productCode,
        String productName,
        String unitName,
        Boolean productActive,
        BigDecimal unitPrice,
        Instant updatedAt) {

    /** Phải gọi trong transaction và product đã được JOIN FETCH. */
    public static PriceListItemResponse withProduct(PriceListItem item) {
        Product product = item.getProduct();
        return new PriceListItemResponse(item.getPriceList().getId(), product.getId(), product.getCode(),
                product.getName(), product.getUnit().getName(), product.isActive(), item.getUnitPrice(),
                item.getUpdatedAt());
    }

    /** Chỉ đọc id của quan hệ LAZY → Hibernate không phải tải SP / bảng giá. */
    public static PriceListItemResponse compact(PriceListItem item) {
        return new PriceListItemResponse(item.getPriceList().getId(), item.getProduct().getId(), null, null, null,
                null, item.getUnitPrice(), item.getUpdatedAt());
    }
}
