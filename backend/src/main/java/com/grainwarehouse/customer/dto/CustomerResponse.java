package com.grainwarehouse.customer.dto;

import com.grainwarehouse.customer.entity.Customer;
import com.grainwarehouse.customer.entity.CustomerType;

import java.time.Instant;

public record CustomerResponse(
        Long id,
        String code,
        String name,
        CustomerType type,
        String phone,
        String contactName,
        String address,
        String note,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getCode(),
                customer.getName(),
                customer.getType(),
                customer.getPhone(),
                customer.getContactName(),
                customer.getAddress(),
                customer.getNote(),
                customer.isActive(),
                customer.getCreatedAt(),
                customer.getUpdatedAt());
    }
}
