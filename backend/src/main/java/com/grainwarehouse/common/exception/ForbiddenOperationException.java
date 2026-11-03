package com.grainwarehouse.common.exception;

import lombok.Getter;

/**
 * Đã đăng nhập đúng nhưng chưa được làm việc này → 403 với mã lỗi riêng để frontend biết phải làm gì,
 * ví dụ PASSWORD_CHANGE_REQUIRED (mở màn hình đổi mật khẩu — BR-POR-03) hay ACCOUNT_DISABLED (đăng xuất — BR-POR-10).
 */
@Getter
public class ForbiddenOperationException extends RuntimeException {

    private final String code;

    public ForbiddenOperationException(String code, String message) {
        super(message);
        this.code = code;
    }
}
