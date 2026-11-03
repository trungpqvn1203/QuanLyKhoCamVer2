package com.grainwarehouse.portal.dto;

import com.grainwarehouse.product.entity.Product;

/** SP khách được thấy (BR-POR-06): KHÔNG có giá, KHÔNG có tồn. */
public record PortalProductResponse(
        Long id,
        String code,
        String name,
        String unitName,
        String categoryName) {

    public static PortalProductResponse from(Product product) {
        return new PortalProductResponse(product.getId(), product.getCode(), product.getName(),
                product.getUnit().getName(), product.getCategory().getName());
    }
}
