package com.grainwarehouse.pricelist.entity;

import com.grainwarehouse.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Bảng giá bán có tên: "Giá lẻ", "Giá đại lý", "Giá trang trại"... (BR-PRC-01..03). */
@Getter
@Setter
@Entity
@Table(name = "price_list")
public class PriceList extends AuditableEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private boolean active = true;
}
