package com.grainwarehouse.common.exception;

import lombok.Getter;

/**
 * Dữ liệu gửi lên sai ở một field cụ thể nhưng chỉ service mới kiểm tra được
 * (ví dụ ngày chứng từ ở tương lai theo giờ Việt Nam, SP lặp ở hai dòng)
 * → HTTP 400 VALIDATION_FAILED kèm details[{field, message}], giống lỗi Bean Validation
 * để frontend gắn lỗi vào đúng ô nhập.
 */
@Getter
public class InvalidRequestException extends RuntimeException {

    private final String field;

    public InvalidRequestException(String field, String message) {
        super(message);
        this.field = field;
    }
}
