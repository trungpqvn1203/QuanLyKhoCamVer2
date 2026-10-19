package com.grainwarehouse.inventory;

import com.grainwarehouse.inventory.entity.StockDocumentItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** BR-MON-05: thành tiền = số lượng × đơn giá, làm tròn HALF_UP về đồng. Không cần Spring/DB. */
class StockDocumentItemTest {

    // Các ca .5 phân biệt HALF_UP với HALF_EVEN (làm tròn ngân hàng): 0.5 → 1 (HALF_EVEN cho 0),
    // 2.5 → 3 (HALF_EVEN cho 2). Nếu ai đổi RoundingMode, test này fail.
    @ParameterizedTest(name = "{0} × {1} = {2}")
    @CsvSource({
            "3, 12345.50, 37037",
            "1, 0.50, 1",
            "1, 2.50, 3",
            "1, 0.49, 0",
            "10, 250000, 2500000",
            "0, 99999.99, 0",
            "7, 0, 0"
    })
    @DisplayName("BR-MON-05: thành tiền làm tròn HALF_UP về đồng")
    void lineAmountShouldRoundHalfUpToWholeDong(int quantity, String unitPrice, String expected) {
        BigDecimal amount = StockDocumentItem.lineAmount(quantity, new BigDecimal(unitPrice));

        assertThat(amount).isEqualByComparingTo(expected);
        assertThat(amount.scale()).isZero();
    }
}
