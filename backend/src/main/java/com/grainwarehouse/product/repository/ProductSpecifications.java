package com.grainwarehouse.product.repository;

import com.grainwarehouse.common.repository.CommonSpecifications;
import com.grainwarehouse.product.dto.ProductSearchCriteria;
import com.grainwarehouse.product.entity.Product;
import org.springframework.data.jpa.domain.Specification;

/** Ghép tiêu chí lọc sản phẩm thành điều kiện SQL; thêm tiêu chí mới = thêm một dòng ở đây. */
public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> from(ProductSearchCriteria criteria) {
        return Specification.allOf(
                CommonSpecifications.keywordContains(criteria.keyword(), "code", "name"),
                CommonSpecifications.equal("category.id", criteria.categoryId()),
                CommonSpecifications.hasActive(criteria.active()));
    }
}
