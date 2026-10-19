package com.grainwarehouse.attendance.entity;

import java.math.BigDecimal;

/** Trạng thái một ngày công (BR-ATT-01). Phải khớp CHECK ck_attendance_status trong migration V11. */
public enum DayStatus {
    FULL(BigDecimal.ONE),
    HALF(new BigDecimal("0.5")),
    ABSENT(BigDecimal.ZERO);

    private final BigDecimal workUnits;

    DayStatus(BigDecimal workUnits) {
        this.workUnits = workUnits;
    }

    /** Số công tính lương: 1 / 0,5 / 0 */
    public BigDecimal workUnits() {
        return workUnits;
    }
}
