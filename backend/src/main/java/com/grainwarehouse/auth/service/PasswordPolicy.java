package com.grainwarehouse.auth.service;

import com.grainwarehouse.common.exception.InvalidRequestException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/** Quy tắc tên đăng nhập và mật khẩu — một chỗ cho tạo tài khoản, đổi mật khẩu, đặt lại mật khẩu. */
public final class PasswordPolicy {

    public static final int MIN_PASSWORD_LENGTH = 8;

    /** BCrypt chỉ dùng 72 byte đầu: phần dư bị bỏ qua âm thầm → hai mật khẩu chỉ khác ở cuối sẽ "trùng nhau". */
    public static final int MAX_PASSWORD_BYTES = 72;

    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9._-]{3,50}$");

    private PasswordPolicy() {
    }

    /** "  Lan.BH " → "lan.bh". Cùng quy tắc với CHECK ck_app_user_username (V14). */
    public static String normalizeUsername(String field, String raw) {
        String username = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (!USERNAME.matcher(username).matches()) {
            throw new InvalidRequestException(field,
                    "Tên đăng nhập 3–50 ký tự, chỉ gồm chữ không dấu, số, dấu chấm, gạch dưới, gạch ngang");
        }
        return username;
    }

    public static void checkPassword(String field, String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new InvalidRequestException(field, "Mật khẩu tối thiểu " + MIN_PASSWORD_LENGTH + " ký tự");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new InvalidRequestException(field,
                    "Mật khẩu quá dài (tối đa " + MAX_PASSWORD_BYTES + " byte; chữ có dấu tính 2–3 byte)");
        }
    }
}
