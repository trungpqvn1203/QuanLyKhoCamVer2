package com.grainwarehouse.delivery.dto;

import com.grainwarehouse.employee.entity.Employee;

/**
 * Lái xe chọn được cho chuyến = nhân viên đang làm. Chỉ mã, tên, SĐT — quản lý kho không có quyền xem màn nhân viên
 * (lương, tạm ứng), nên chuyến giao có endpoint riêng thay vì gọi /api/employees.
 */
public record DriverResponse(Long id, String code, String name, String phone) {

    public static DriverResponse from(Employee employee) {
        return new DriverResponse(employee.getId(), employee.getCode(), employee.getName(), employee.getPhone());
    }
}
