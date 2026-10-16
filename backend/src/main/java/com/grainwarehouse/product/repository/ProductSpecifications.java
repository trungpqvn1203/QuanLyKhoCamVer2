package com.grainwarehouse.product.repository;

import com.grainwarehouse.product.entity.Product;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

/**
 * Điều kiện lọc danh sách sản phẩm, ghép động trong SQL (WHERE) để phân trang đúng.
 * Mỗi hàm trả về null khi không có điều kiện → Spring Data bỏ qua điều kiện đó.
 */
public final class ProductSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private ProductSpecifications() {
    }

    /** Mã hoặc tên chứa từ khóa, không phân biệt hoa thường. */
    public static Specification<Product> keywordContains(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String pattern = "%" + escapeLike(keyword.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("code")), pattern, LIKE_ESCAPE),
                cb.like(cb.lower(root.get("name")), pattern, LIKE_ESCAPE));
    }

    public static Specification<Product> inCategory(Long categoryId) {
        return categoryId == null ? null
                : (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Product> hasActive(Boolean active) {
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
