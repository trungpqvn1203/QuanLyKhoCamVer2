package com.grainwarehouse.payroll;

import com.grainwarehouse.payroll.service.PayCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PayCalculatorTest {

    @ParameterizedTest(name = "{0} × {1} = {2}")
    @CsvSource({
            "22.5,   300000,    6750000",
            "41.235, 52500,     2164838",   // 2.164.837,5 → HALF_UP lên
            "0.5,    333333.33, 166667",    // 166.666,665 → 166.667
            "1.001,  499.50,    500",       // 499,9995 → 500
            "0,      300000,    0"
    })
    @DisplayName("BR-PAY-01: số lượng × đơn giá, làm tròn HALF_UP về đồng")
    void shouldRoundHalfUpToWholeDong(String quantity, String rate, String expected) {
        BigDecimal amount = PayCalculator.tonAmount(new BigDecimal(quantity), new BigDecimal(rate));

        assertThat(amount).isEqualByComparingTo(expected);
        assertThat(amount.scale()).isZero();
    }

    @Test
    @DisplayName("BR-EMP-02: không hưởng theo cách đó (đơn giá null) → 0, dù có công / có tấn")
    void shouldBeZeroWithoutRate() {
        assertThat(PayCalculator.dayAmount(new BigDecimal("22"), null)).isEqualByComparingTo("0");
        assertThat(PayCalculator.tonAmount(new BigDecimal("12.5"), null)).isEqualByComparingTo("0");
    }
}
