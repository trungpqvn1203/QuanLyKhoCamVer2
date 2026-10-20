package com.grainwarehouse.attendance.entity;

import com.grainwarehouse.common.entity.AuditableEntity;
import com.grainwarehouse.employee.entity.Employee;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Một ô của bảng công: nhân viên × ngày (BR-ATT-01). */
@Getter
@Entity
@Table(name = "attendance")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attendance extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false)
    private Employee employee;

    // Nhân viên và ngày là "khóa" của ô — không đổi sau khi tạo; ghi nhầm ô thì xóa ô đó
    @Column(name = "work_date", nullable = false, updatable = false)
    private LocalDate workDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_status", nullable = false, length = 10)
    private DayStatus dayStatus;

    @Column(nullable = false, precision = 8, scale = 3)
    private BigDecimal tons;

    @Column(length = 500)
    private String note;

    public Attendance(Employee employee, LocalDate workDate) {
        this.employee = employee;
        this.workDate = workDate;
    }

    /** Service đã kiểm BR-ATT-02 trước khi gọi; CHECK ck_attendance_absent_no_tons là chốt chặn cuối. */
    public void record(DayStatus dayStatus, BigDecimal tons, String note) {
        this.dayStatus = dayStatus;
        this.tons = tons;
        this.note = note;
    }
}
