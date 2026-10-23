package com.grainwarehouse.common.importing;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Một dòng dữ liệu đã đọc khỏi Excel, chưa ép kiểu. Các hàm đọc trả null khi ô trống hoặc sai kiểu,
 * và ghi lỗi sai kiểu vào dòng — không ném exception, để gom được MỌI lỗi của file trong một lần kiểm tra.
 * Không phụ thuộc Apache POI: giá trị ô đã được ExcelWorkbooks chuyển thành String hoặc BigDecimal.
 */
public final class ImportRow {

    private final int number;
    private final List<ImportColumn> columns;
    private final Map<String, Object> values;
    // LinkedHashMap: giữ thứ tự lỗi theo thứ tự phát hiện; mỗi field tối đa một lỗi (lỗi đầu tiên)
    private final Map<String, ImportError> errors = new LinkedHashMap<>();

    public ImportRow(int number, List<ImportColumn> columns, Map<String, Object> values) {
        this.number = number;
        this.columns = columns;
        this.values = values;
    }

    public int number() {
        return number;
    }

    /** Chuỗi đã cắt khoảng trắng; ô số (ví dụ SĐT bị Excel đổi sang số) được đọc lại thành chuỗi. */
    public String text(String key) {
        Object value = values.get(key);
        if (value instanceof BigDecimal number) {
            return number.toPlainString();
        }
        return (String) value;
    }

    /** Ô số hoặc chuỗi số "25.5". Không làm tròn — sai số chữ số thập phân do @Digits của request báo. */
    public BigDecimal decimal(String key) {
        Object value = values.get(key);
        if (value == null || value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        try {
            return new BigDecimal(((String) value).replace(" ", ""));
        } catch (NumberFormatException e) {
            error(key, "Phải là số (dùng dấu chấm cho phần thập phân), đang là '" + value + "'");
            return null;
        }
    }

    /** Số nguyên: 10 và 10.0 hợp lệ, 10.5 bị từ chối (không âm thầm cắt thành 10). */
    public Integer integer(String key) {
        BigDecimal value = decimal(key);
        if (value == null) {
            return null;
        }
        try {
            return value.stripTrailingZeros().intValueExact();
        } catch (ArithmeticException e) {
            error(key, "Phải là số nguyên, đang là " + value.toPlainString());
            return null;
        }
    }

    /**
     * Giá trị chọn từ dropdown: khớp nhãn tiếng Việt ("Đại lý") hoặc mã enum ("DEALER"), không phân biệt hoa thường.
     * Người dùng có thể gõ tay hoặc dán đè lên ô có dropdown — Excel không chặn trường hợp dán.
     */
    public <E extends Enum<E>> E option(String key, Map<String, E> labels) {
        String value = text(key);
        if (value == null) {
            return null;
        }
        for (Map.Entry<String, E> entry : labels.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(value) || entry.getValue().name().equalsIgnoreCase(value)) {
                return entry.getValue();
            }
        }
        error(key, "Giá trị '" + value + "' không hợp lệ. Chọn một trong: " + String.join(", ", labels.keySet()));
        return null;
    }

    /** Ghi lỗi cho một field; field đã có lỗi thì giữ lỗi cũ (thường là lỗi gốc, ví dụ sai kiểu trước "không được trống"). */
    public void error(String field, String message) {
        String key = field == null ? "" : field;
        errors.putIfAbsent(key, new ImportError(number, field, headerOf(field), message));
    }

    public boolean hasError(String field) {
        return errors.containsKey(field);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public List<ImportError> errors() {
        return new ArrayList<>(errors.values());
    }

    private String headerOf(String field) {
        if (field == null) {
            return null;
        }
        return columns.stream()
                .filter(column -> column.key().equals(field))
                .map(ImportColumn::header)
                .findFirst()
                .orElse(null);
    }

    /** Khóa so trùng trong file — khớp cách DB so trùng tên: trim (service) + lower(name) (unique index). */
    public static String normalizeKey(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
