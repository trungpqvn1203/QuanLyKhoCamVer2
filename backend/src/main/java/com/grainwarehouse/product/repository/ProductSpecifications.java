package com.grainwarehouse.product.repository;

import com.grainwarehouse.common.repository.CommonSpecifications;
import com.grainwarehouse.product.entity.Product;
import org.springframework.data.jpa.domain.Specification;

/**
 * Điều kiện lọc danh sách sản phẩm, ghép động trong SQL (WHERE) để phân trang đúng.
 * Mỗi hàm trả về null khi không có điều kiện → Spring Data bỏ qua điều kiện đó.
 * Từ khóa và trạng thái dùng chung với các module khác (CommonSpecifications); danh mục là điều kiện riêng.
 */
public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    /** Mã hoặc tên chứa từ khóa, không phân biệt hoa thường. */
    public static Specification<Product> keywordContains(String keyword) {
        return CommonSpecifications.keywordContains(keyword, "code", "name");
    }

    public static Specification<Product> inCategory(Long categoryId) {
        return categoryId == null ? null
                : (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Product> hasActive(Boolean active) {
        return CommonSpecifications.hasActive(active);
    }
}
