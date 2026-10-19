package com.grainwarehouse.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mã tự sinh không bị cắt cụt khi số thứ tự vượt độ rộng đệm (migration V10).
 * Lỗi cũ: 'KHO' || lpad('1050', 3, '0') = 'KHO105' → mã thứ 1050..1059 trùng nhau.
 * Kiểm hàm trực tiếp thay vì setval: setval KHÔNG rollback theo transaction, sẽ đẩy sequence thật lên mãi.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CodeGenerationPersistenceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @ParameterizedTest(name = "format_code({0}, {1}, {2}) = {3}")
    @CsvSource({
            "KHO, 7,       3, KHO007",
            "KHO, 999,     3, KHO999",
            "KHO, 1050,    3, KHO1050",
            "KHO, 1051,    3, KHO1051",
            "KH,  10000,   4, KH10000",
            "SP,  1234567, 6, SP1234567"
    })
    @DisplayName("Đệm số 0 khi ngắn hơn, giữ nguyên khi dài hơn - không cắt")
    void shouldPadWithoutTruncating(String prefix, long value, int width, String expected) {
        String code = jdbcTemplate.queryForObject("SELECT format_code(?, ?, ?)", String.class, prefix, value, width);

        assertThat(code).isEqualTo(expected);
    }

    @Test
    @DisplayName("Mọi cột mã tự sinh dùng format_code, không còn lpad trực tiếp")
    void everyGeneratedCodeColumnShouldUseFormatCode() {
        List<String> defaults = jdbcTemplate.queryForList("""
                SELECT c.relname || ': ' || pg_get_expr(d.adbin, d.adrelid)
                FROM pg_attrdef d
                JOIN pg_class c ON c.oid = d.adrelid
                JOIN pg_attribute a ON a.attrelid = d.adrelid AND a.attnum = d.adnum
                WHERE a.attname = 'code' AND pg_get_expr(d.adbin, d.adrelid) LIKE '%nextval%'
                """, String.class);

        assertThat(defaults)
                .hasSize(5)
                .allSatisfy(expression -> assertThat(expression).contains("format_code(").doesNotContain("lpad("));
    }
}
