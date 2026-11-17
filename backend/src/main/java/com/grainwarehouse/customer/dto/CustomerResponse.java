package com.grainwarehouse.customer.dto;

import com.grainwarehouse.customer.entity.Customer;
import com.grainwarehouse.customer.entity.CustomerType;
import com.grainwarehouse.pricelist.entity.PriceList;

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
        Long priceListId,
        String priceListName,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static CustomerResponse from(Customer customer) {
        PriceList priceList = customer.getPriceList();
        return new CustomerResponse(
                customer.getId(),
                customer.getCode(),
                customer.getName(),
                customer.getType(),
                customer.getPhone(),
                customer.getContactName(),
                customer.getAddress(),
                customer.getNote(),
                priceList == null ? null : priceList.getId(),
                priceList == null ? null : priceList.getName(),
                customer.isActive(),
                customer.getCreatedAt(),
                customer.getUpdatedAt());
    }
}
