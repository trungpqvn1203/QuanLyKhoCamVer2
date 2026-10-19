package com.grainwarehouse.supplier.dto;

import com.grainwarehouse.supplier.entity.ProductSupplier;
import com.grainwarehouse.supplier.entity.Supplier;

/** Một NCC đang được phép cung cấp SP (chiều ngược của SupplierProductResponse). */
public record ProductSupplierResponse(
        Long supplierId,
        String supplierCode,
        String supplierName,
        String contactName,
        String phone,
        boolean supplierActive) {

    public static ProductSupplierResponse from(ProductSupplier link) {
        Supplier supplier = link.getSupplier();
        return new ProductSupplierResponse(
                supplier.getId(),
                supplier.getCode(),
                supplier.getName(),
                supplier.getContactName(),
                supplier.getPhone(),
                supplier.isActive());
    }
}
