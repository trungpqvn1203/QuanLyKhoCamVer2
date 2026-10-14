package com.grainwarehouse.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Trường chung cho mọi Entity: id + createdAt.
 * Dùng trực tiếp cho bảng bất biến (ví dụ stock_transaction) — không có updatedAt.
 * {@code @MappedSuperclass}: không phải bảng riêng, các cột được "chép" vào bảng con.
 */
@Getter
@MappedSuperclass
public abstract class BaseEntity {

    // Khớp với GENERATED ALWAYS AS IDENTITY: DB cấp id, Hibernate đọc lại sau INSERT
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
