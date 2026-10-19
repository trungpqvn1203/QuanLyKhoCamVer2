package com.grainwarehouse.employee.entity;

import com.grainwarehouse.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;

import java.math.BigDecimal;

/** Nhân viên (BR-EMP-01..03). */
@Getter
@Setter
@Entity
@Table(name = "employee")
public class Employee extends AuditableEntity {

    // DB tự sinh qua DEFAULT nextval(...) (BR-EMP-01) — cùng cách với Customer.code
    @Generated
    @Setter(AccessLevel.NONE)
    @Column(nullable = false, length = 20, insertable = false, updatable = false)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    // Tùy chọn; có thì luôn ở dạng đã chuẩn hóa (BR-EMP-03)
    @Column(length = 20)
    private String phone;

    // BR-EMP-02: null = không hưởng lương theo ngày
    @Column(name = "daily_wage", precision = 15, scale = 2)
    private BigDecimal dailyWage;

    // BR-EMP-02: null = không hưởng lương theo tấn bốc vác
    @Column(name = "ton_rate", precision = 15, scale = 2)
    private BigDecimal tonRate;

    @Column(nullable = false)
    private boolean active = true;
}
