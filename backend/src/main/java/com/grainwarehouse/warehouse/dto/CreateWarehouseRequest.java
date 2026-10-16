package com.grainwarehouse.warehouse.dto;

import com.grainwarehouse.warehouse.entity.WarehouseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Không có trường code: mã do hệ thống tự sinh (BR-WH-02). */
public record CreateWarehouseRequest(
        @NotBlank(message = "Tên kho không được để trống")
        @Size(max = 200, message = "Tên kho tối đa 200 ký tự")
        String name,

        @NotNull(message = "Loại kho không được để trống")
        WarehouseType type,

        @NotBlank(message = "Địa chỉ kho không được để trống")
        @Size(max = 500, message = "Địa chỉ kho tối đa 500 ký tự")
        String address) {
}
