package com.grainwarehouse.inventory.repository;

/**
 * Kết quả GROUP BY: một khóa (id SP hoặc id kho) + tổng số lượng.
 * Interface projection của Spring Data: tên getter khớp alias trong câu JPQL ("as id", "as quantity").
 * SUM của cột INTEGER trả về Long (theo chuẩn JPA).
 */
public interface QuantityByKey {

    Long getId();

    Long getQuantity();
}
