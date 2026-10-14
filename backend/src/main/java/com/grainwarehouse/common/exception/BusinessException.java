package com.grainwarehouse.common.exception;

/** Vi phạm quy tắc nghiệp vụ (ví dụ xuất vượt tồn) → HTTP 409, mã BUSINESS_RULE_VIOLATION. */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
