package com.grainwarehouse.common.importing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Ép kiểu một dòng Excel: sai kiểu → lỗi gắn đúng cột, không ném exception, không âm thầm làm tròn. */
class ImportRowTest {

    private static final List<ImportColumn> COLUMNS = List.of(
            ImportColumn.required("name", "Tên", null),
            ImportColumn.required("weight", "Khối lượng", null),
            ImportColumn.optional("minStock", "Tồn tối thiểu", null),
            ImportColumn.required("type", "Loại", null));

    private enum Type { DEALER, FARM }

    private static final Map<String, Type> TYPES = new LinkedHashMap<>();

    static {
        TYPES.put("Đại lý", Type.DEALER);
        TYPES.put("Trang trại", Type.FARM);
    }

    private static ImportRow row(Map<String, Object> values) {
        return new ImportRow(7, COLUMNS, values);
    }

    @Test
    @DisplayName("Ô số và chuỗi số đều đọc được thành BigDecimal, giữ nguyên chữ số")
    void shouldReadDecimals() {
        ImportRow row = row(Map.of("weight", new BigDecimal("25.5"), "minStock", "1 000"));

        assertThat(row.decimal("weight")).isEqualByComparingTo("25.5");
        assertThat(row.integer("minStock")).isEqualTo(1000);
        assertThat(row.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("Chữ trong ô số → lỗi tại đúng cột, kèm số dòng Excel và tiêu đề cột")
    void shouldReportNonNumber() {
        ImportRow row = row(Map.of("weight", "25kg"));

        assertThat(row.decimal("weight")).isNull();
        assertThat(row.errors()).singleElement().satisfies(error -> {
            assertThat(error.row()).isEqualTo(7);
            assertThat(error.field()).isEqualTo("weight");
            assertThat(error.column()).isEqualTo("Khối lượng");
        });
    }

    @Test
    @DisplayName("Số nguyên: 10.0 hợp lệ, 10.5 bị từ chối (không cắt thành 10)")
    void shouldRejectFractionForInteger() {
        assertThat(row(Map.of("minStock", new BigDecimal("10.0"))).integer("minStock")).isEqualTo(10);

        ImportRow row = row(Map.of("minStock", new BigDecimal("10.5")));
        assertThat(row.integer("minStock")).isNull();
        assertThat(row.hasError("minStock")).isTrue();
    }

    @Test
    @DisplayName("Lựa chọn: khớp nhãn hoặc mã enum, không phân biệt hoa thường; giá trị lạ → lỗi liệt kê lựa chọn")
    void shouldMatchOptions() {
        assertThat(row(Map.of("type", "đại LÝ")).option("type", TYPES)).isEqualTo(Type.DEALER);
        assertThat(row(Map.of("type", "farm")).option("type", TYPES)).isEqualTo(Type.FARM);

        ImportRow row = row(Map.of("type", "Siêu thị"));
        assertThat(row.option("type", TYPES)).isNull();
        assertThat(row.errors().get(0).message()).contains("Đại lý, Trang trại");
    }

    @Test
    @DisplayName("Ô số trong cột chữ (SĐT bị Excel đổi sang số) đọc lại thành chuỗi, không ra dạng 9.01E8")
    void shouldReadNumberAsText() {
        assertThat(row(Map.of("name", new BigDecimal("901234567"))).text("name")).isEqualTo("901234567");
    }

    @Test
    @DisplayName("Mỗi cột chỉ giữ lỗi đầu tiên; lỗi cả dòng không có cột")
    void shouldKeepFirstErrorPerField() {
        ImportRow row = row(Map.of());
        row.error("name", "Lỗi gốc");
        row.error("name", "Lỗi sau");
        row.error(null, "Lỗi cả dòng");

        assertThat(row.errors()).extracting(ImportError::message).containsExactly("Lỗi gốc", "Lỗi cả dòng");
        assertThat(row.errors().get(1).column()).isNull();
    }
}
