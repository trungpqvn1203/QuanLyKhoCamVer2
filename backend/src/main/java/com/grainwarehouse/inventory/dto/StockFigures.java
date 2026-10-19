package com.grainwarehouse.inventory.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Bộ số tồn của một SP trong một phạm vi (mọi kho, hoặc một kho).
 * quantity: tồn thật (inventory). incoming: tổng SL đặt của phiếu nhập còn Nháp — chưa phải tồn, không cộng vào (BR-STK-06).
 */
public record StockFigures(long quantity, BigDecimal tons, long incoming) {

    public static StockFigures of(long quantity, BigDecimal netWeightKg, long incoming) {
        return new StockFigures(quantity, tons(quantity, netWeightKg), incoming);
    }

    /**
     * BR-PRD-06, BR-STK-02: tấn = số lượng × khối lượng tịnh (kg) / 1000, làm tròn HALF_UP, 3 chữ số lẻ (= đến kg).
     * Ví dụ 120 bao × 25 kg = 3,000 tấn; 7 bao × 22,5 kg = 157,5 kg = 0,158 tấn.
     */
    public static BigDecimal tons(long quantity, BigDecimal netWeightKg) {
        return netWeightKg.multiply(BigDecimal.valueOf(quantity)).divide(BigDecimal.valueOf(1000), 3, RoundingMode.HALF_UP);
    }
}
