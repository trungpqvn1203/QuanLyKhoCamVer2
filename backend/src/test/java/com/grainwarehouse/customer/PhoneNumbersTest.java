package com.grainwarehouse.customer;

import com.grainwarehouse.customer.service.PhoneNumbers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneNumbersTest {

    @ParameterizedTest(name = "\"{0}\" → {1}")
    @CsvSource(delimiter = '|', value = {
            "0901234567     | 0901234567",
            "0901 234 567   | 0901234567",
            "0901.234.567   | 0901234567",
            "0901-234-567   | 0901234567",
            "' 0283 456 7890 ' | 02834567890"
    })
    @DisplayName("BR-CUS-06: các cách gõ của cùng một số chuẩn hóa về cùng một chuỗi")
    void shouldNormalizeSeparators(String raw, String expected) {
        assertThat(PhoneNumbers.normalize(raw)).isEqualTo(expected);
        assertThat(PhoneNumbers.isValid(expected)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"901234567", "090123456", "090123456789", "+84901234567", "09012a4567", ""})
    @DisplayName("BR-CUS-06: sai định dạng sau chuẩn hóa - thiếu số 0 đầu, sai độ dài, có ký tự lạ")
    void shouldRejectInvalidFormat(String normalized) {
        assertThat(PhoneNumbers.isValid(normalized)).isFalse();
    }
}
