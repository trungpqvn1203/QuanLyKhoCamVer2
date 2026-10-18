package com.grainwarehouse.common.repository;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;
import java.util.Locale;

/**
 * Điều kiện lọc dùng chung cho mọi danh sách (Sản phẩm, NCC, Kho...), ghép động trong SQL để phân trang đúng.
 * Mỗi hàm trả về null khi không có điều kiện → Spring Data bỏ qua điều kiện đó.
 * Điều kiện riêng của từng module (ví dụ lọc theo danh mục) vẫn nằm ở *Specifications của module đó.
 */
public final class CommonSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private CommonSpecifications() {
    }

    /**
     * Một trong các thuộc tính chứa từ khóa, không phân biệt hoa thường, tự cắt khoảng trắng hai đầu.
     * Tên thuộc tính do code truyền vào (không lấy từ request) → người dùng không lọc được cột tùy ý.
     */
    public static <T> Specification<T> keywordContains(String keyword, String... attributes) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String pattern = "%" + escapeLike(keyword.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.or(Arrays.stream(attributes)
                .map(attribute -> cb.like(cb.lower(root.get(attribute)), pattern, LIKE_ESCAPE))
                .toArray(Predicate[]::new));
    }

    public static <T> Specification<T> hasActive(Boolean active) {
        return active == null ? null
                : (root, query, cb) -> cb.equal(root.get("active"), active);
    }

    // Người dùng gõ "%" hay "_" phải được hiểu là ký tự thường, không phải ký tự đại diện của LIKE
    private static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
