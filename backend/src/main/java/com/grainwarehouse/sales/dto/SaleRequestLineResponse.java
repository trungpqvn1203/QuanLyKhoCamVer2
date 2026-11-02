package com.grainwarehouse.sales.dto;

import com.grainwarehouse.product.entity.Product;
import com.grainwarehouse.sales.entity.SaleRequestItem;

/** Một dòng khách đặt (BR-SAL-20) — để đối chiếu với dòng phiếu thực giao. */
public record SaleRequestLineResponse(
        Long id,
        Long productId,
        String productCode,
        String productName,
        String unitName,
        int quantity) {

    public static SaleRequestLineResponse from(SaleRequestItem item) {
        Product product = item.getProduct();
        return new SaleRequestLineResponse(item.getId(), product.getId(), product.getCode(), product.getName(),
                product.getUnit().getName(), item.getQuantity());
    }
}
