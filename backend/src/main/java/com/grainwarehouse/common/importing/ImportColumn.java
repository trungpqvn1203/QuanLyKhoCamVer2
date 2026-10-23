package com.grainwarehouse.common.importing;

import java.util.List;

/**
 * Một cột của file mẫu Excel.
 *
 * @param key      trùng tên field của request tạo mới (name, phone...) → lỗi Bean Validation gắn đúng cột
 * @param header   tiêu đề hiển thị; cột bắt buộc được thêm " *" khi in ra file
 * @param note     hướng dẫn in ở sheet "Hướng dẫn"
 * @param options  giá trị cho dropdown; rỗng = ô nhập tự do
 * @param text     định dạng ô là Text — để Excel không bỏ số 0 đầu của SĐT (0901… → 901…)
 */
public record ImportColumn(String key, String header, boolean required, String note, List<String> options,
                           boolean text) {

    public static ImportColumn required(String key, String header, String note) {
        return new ImportColumn(key, header, true, note, List.of(), false);
    }

    public static ImportColumn optional(String key, String header, String note) {
        return new ImportColumn(key, header, false, note, List.of(), false);
    }

    public ImportColumn withOptions(List<String> values) {
        return new ImportColumn(key, header, required, note, List.copyOf(values), text);
    }

    public ImportColumn asText() {
        return new ImportColumn(key, header, required, note, options, true);
    }

    /** Tiêu đề in trên file — cũng là chuỗi phải khớp khi đọc file upload. */
    public String headerText() {
        return required ? header + " *" : header;
    }
}
