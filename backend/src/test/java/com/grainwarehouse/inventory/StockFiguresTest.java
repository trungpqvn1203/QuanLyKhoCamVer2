package com.grainwarehouse.inventory;

import com.grainwarehouse.inventory.dto.StockFigures;
import com.grainwarehouse.inventory.dto.StockStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Quy tắc tính tấn và tình trạng tồn — không cần Spring/DB. */
class StockFiguresTest {

    @ParameterizedTest(name = "{0} × {1} kg = {2} tấn")
    @CsvSource({
            "120, 25, 3.000",
            "7, 22.5, 0.158",   // 157,5 kg → làm tròn HALF_UP lên 0,158 tấn
            "3, 22.5, 0.068",   // 67,5 kg → 0,068
            "1, 0.4, 0.000",    // 0,4 kg → dưới nửa kg → 0,000
            "1500, 1, 1.500",   // SP bán theo KG: 1 đơn vị = 1 kg
            "0, 25, 0.000"
    })
    @DisplayName("BR-STK-02: tấn = SL × kg / 1000, HALF_UP, 3 chữ số lẻ")
    void tonsShouldRoundHalfUpToKilogram(long quantity, String netWeightKg, String expected) {
        BigDecimal tons = StockFigures.tons(quantity, new BigDecimal(netWeightKg));

        assertThat(tons).isEqualByComparingTo(expected);
        assertThat(tons.scale()).isEqualTo(3);
    }

    @Test
    @DisplayName("BR-STK-04: Hết hàng khi = 0; Sắp hết khi 0 < tồn ≤ mức tối thiểu; không đặt mức thì không có Sắp hết")
    void stockStatusRules() {
        assertThat(StockStatus.of(0, null)).isEqualTo(StockStatus.OUT);
        assertThat(StockStatus.of(0, 50)).isEqualTo(StockStatus.OUT);
        assertThat(StockStatus.of(1, 50)).isEqualTo(StockStatus.LOW);
        assertThat(StockStatus.of(50, 50)).isEqualTo(StockStatus.LOW);   // ≤ — đúng bằng mức vẫn là Sắp hết
        assertThat(StockStatus.of(51, 50)).isEqualTo(StockStatus.IN_STOCK);
        assertThat(StockStatus.of(5, null)).isEqualTo(StockStatus.IN_STOCK);
        assertThat(StockStatus.of(3, 0)).isEqualTo(StockStatus.IN_STOCK); // mức 0 = chỉ cảnh báo khi hết hẳn
    }
}
