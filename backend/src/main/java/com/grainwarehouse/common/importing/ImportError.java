package com.grainwarehouse.common.importing;

/**
 * Một lỗi trong file nhập.
 *
 * @param row     số dòng thật trong Excel (dòng tiêu đề là 1) để người dùng tìm thẳng tới
 * @param field   tên field (name, phone...); null = lỗi cả dòng, không thuộc riêng cột nào
 * @param column  tiêu đề cột tương ứng với field, để hiển thị
 */
public record ImportError(int row, String field, String column, String message) {
}
