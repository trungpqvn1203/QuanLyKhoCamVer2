package com.grainwarehouse.supplier.dto;

import com.grainwarehouse.supplier.entity.Supplier;

import java.time.Instant;

public record SupplierResponse(
        Long id,
        String code,
        String name,
        String contactName,
        String phone,
        String email,
        String address,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static SupplierResponse from(Supplier supplier) {
        return new SupplierResponse(
                supplier.getId(),
                supplier.getCode(),
                supplier.getName(),
                supplier.getContactName(),
                supplier.getPhone(),
                supplier.getEmail(),
                supplier.getAddress(),
                supplier.isActive(),
                supplier.getCreatedAt(),
                supplier.getUpdatedAt());
    }
}
