package com.grainwarehouse.employee.dto;

import com.grainwarehouse.employee.entity.Employee;

import java.math.BigDecimal;
import java.time.Instant;

public record EmployeeResponse(
        Long id,
        String code,
        String name,
        String phone,
        BigDecimal dailyWage,
        BigDecimal tonRate,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static EmployeeResponse from(Employee employee) {
        return new EmployeeResponse(
                employee.getId(),
                employee.getCode(),
                employee.getName(),
                employee.getPhone(),
                employee.getDailyWage(),
                employee.getTonRate(),
                employee.isActive(),
                employee.getCreatedAt(),
                employee.getUpdatedAt());
    }
}
