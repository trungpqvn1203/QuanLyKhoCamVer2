package com.grainwarehouse.common.repository;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;
import java.util.Collection;

/**
 * Điều kiện lọc dùng chung cho mọi danh sách (Sản phẩm, NCC, Kho...), ghép động trong SQL để phân trang đúng.
 * Quy ước: tham số lọc null/rỗng → trả về null → Specification.allOf(...) bỏ qua điều kiện đó.
 * Tên thuộc tính luôn do code truyền vào (không lấy từ request) → người dùng không lọc được cột tùy ý.
 * Điều kiện riêng của từng module vẫn nằm ở *Specifications của module đó.
 */
public final class CommonSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    /** Hàm SQL tạo ở migration V5: bỏ dấu tiếng Việt, IMMUTABLE để sau này đánh index được. */
    private static final String UNACCENT_FUNCTION = "immutable_unaccent";

    private CommonSpecifications() {
    }

    /**
     * Một trong các thuộc tính chứa từ khóa — không phân biệt hoa thường, không phân biệt dấu
     * ("cam" khớp "Cám", "DONG" khớp "Đồng"), tự cắt khoảng trắng hai đầu. "%", "_" là ký tự thường.
     */
    public static <T> Specification<T> keywordContains(String keyword, String... attributes) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String pattern = "%" + escapeLike(keyword.trim()) + "%";
        return (root, query, cb) -> {
            // Chuẩn hóa cả hai vế trong SQL bằng cùng một hàm → kết quả nhất quán, không phụ thuộc Java
            Expression<String> normalizedPattern = normalize(cb, cb.literal(pattern));
            return cb.or(Arrays.stream(attributes)
                    .map(attribute -> cb.like(normalize(cb, root.get(attribute)), normalizedPattern, LIKE_ESCAPE))
                    .toArray(Predicate[]::new));
        };
    }

    /** Thuộc tính bằng giá trị; value null → không lọc. Hỗ trợ đường dẫn lồng như "category.id". */
    public static <T> Specification<T> equal(String attribute, Object value) {
        return value == null ? null
                : (root, query, cb) -> cb.equal(path(root, attribute), value);
    }

    /** Thuộc tính nằm trong danh sách; danh sách null/rỗng → không lọc (không phải "không khớp gì"). */
    public static <T> Specification<T> in(String attribute, Collection<?> values) {
        return values == null || values.isEmpty() ? null
                : (root, query, cb) -> path(root, attribute).in(values);
    }

    /**
     * Thuộc tính trong khoảng [from, to], hai đầu đều tính. Thiếu một đầu → khoảng hở
     * (chỉ from: "từ ngày X trở đi"); thiếu cả hai → không lọc.
     */
    public static <T, Y extends Comparable<? super Y>> Specification<T> between(String attribute, Y from, Y to) {
        if (from == null && to == null) {
            return null;
        }
        return (root, query, cb) -> {
            Path<Y> path = path(root, attribute);
            if (from == null) {
                return cb.lessThanOrEqualTo(path, to);
            }
            if (to == null) {
                return cb.greaterThanOrEqualTo(path, from);
            }
            return cb.between(path, from, to);
        };
    }

    /** Trạng thái hoạt động — trường hợp hay dùng nhất của equal(). */
    public static <T> Specification<T> hasActive(Boolean active) {
        return equal("active", active);
    }

    private static Expression<String> normalize(CriteriaBuilder cb, Expression<String> value) {
        return cb.lower(cb.function(UNACCENT_FUNCTION, String.class, value));
    }

    // "category.id" → root.get("category").get("id")
    private static <Y> Path<Y> path(Root<?> root, String attribute) {
        Path<?> path = root;
        for (String part : attribute.split("\\.")) {
            path = path.get(part);
        }
        @SuppressWarnings("unchecked")
        Path<Y> typed = (Path<Y>) path;
        return typed;
    }

    // Người dùng gõ "%" hay "_" phải được hiểu là ký tự thường, không phải ký tự đại diện của LIKE
    private static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
