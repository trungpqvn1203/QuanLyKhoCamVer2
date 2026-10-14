package com.grainwarehouse.common.exception;

/** Trùng dữ liệu duy nhất (tên sản phẩm, tên danh mục...) → HTTP 409, mã DUPLICATE_RESOURCE. */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
