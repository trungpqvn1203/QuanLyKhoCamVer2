package com.grainwarehouse.payroll.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Công thức lương BR-PAY-01, BR-PAY-04 — tách riêng để test bằng số, không cần DB.
 * Mỗi phần (công, tấn bốc xuống, tấn giao hàng) làm tròn HALF_UP về đồng riêng; tổng = tổng ba phần ĐÃ làm tròn
 * (không làm tròn lại tổng).
 */
public final class PayCalculator {

    private PayCalculator() {
    }

    /** 22,5 công × 300.000 = 6.750.000. Không hưởng theo ngày (rate null) → 0. */
    public static BigDecimal dayAmount(BigDecimal workUnits, BigDecimal dailyWage) {
        return amount(workUnits, dailyWage);
    }

    /**
     * 41,235 tấn × 52.500 = 2.164.837,5 → 2.164.838. Không hưởng theo tấn (rate null) → 0.
     * Dùng chung cho tấn bốc xuống và tấn giao hàng: cùng công thức, khác đơn giá (BR-PAY-04).
     */
    public static BigDecimal tonAmount(BigDecimal tons, BigDecimal tonRate) {
        return amount(tons, tonRate);
    }

    private static BigDecimal amount(BigDecimal quantity, BigDecimal rate) {
        if (rate == null) {
            return BigDecimal.ZERO;
        }
        return quantity.multiply(rate).setScale(0, RoundingMode.HALF_UP);
    }
}
