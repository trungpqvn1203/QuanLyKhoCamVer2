package com.grainwarehouse.common.text;

import java.util.regex.Pattern;

/**
 * Chuẩn hóa số điện thoại, dùng chung cho khách hàng và nhân viên (BR-CUS-06, BR-EMP-03):
 * "0901 234 567", "0901.234.567", "0901-234-567" → "0901234567".
 * Phải chuẩn hóa trước khi kiểm tra trùng: UNIQUE so sánh từng ký tự nên hai cách gõ của cùng một số
 * sẽ lọt qua nếu lưu nguyên văn.
 */
public final class PhoneNumbers {

    private static final Pattern SEPARATORS = Pattern.compile("[\\s.\\-]");

    // Khớp CHECK ck_customer_phone (V8), ck_employee_phone (V9): số 0 đầu + 9–10 chữ số (di động 10 số, máy bàn 11 số)
    private static final Pattern VALID = Pattern.compile("0\\d{9,10}");

    private PhoneNumbers() {
    }

    /** Bỏ khoảng trắng, dấu chấm, gạch ngang. */
    public static String normalize(String raw) {
        return SEPARATORS.matcher(raw).replaceAll("");
    }

    public static boolean isValid(String normalized) {
        return VALID.matcher(normalized).matches();
    }
}
