package com.grainwarehouse.product.entity;

/**
 * Đơn vị giao dịch cố định của sản phẩm (BR-PRD-05).
 * Tấn không phải đơn vị giao dịch — chỉ quy đổi khi báo cáo (BR-PRD-06).
 * Phải khớp CHECK ck_product_unit trong migration V2.
 */
public enum ProductUnit {
    BAG,
    KG
}
