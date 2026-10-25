package com.grainwarehouse.common.exception;

import lombok.Getter;

/**
 * Trùng dữ liệu duy nhất (tên sản phẩm, tên danh mục...) → HTTP 409, mã DUPLICATE_RESOURCE.
 * field (tùy chọn): field bị trùng — nhập Excel dùng để gắn lỗi vào đúng cột; API 409 không trả field.
 */
@Getter
public class DuplicateResourceException extends RuntimeException {

    private final String field;

    public DuplicateResourceException(String message) {
        this(null, message);
    }

    public DuplicateResourceException(String field, String message) {
        super(message);
        this.field = field;
    }
}
