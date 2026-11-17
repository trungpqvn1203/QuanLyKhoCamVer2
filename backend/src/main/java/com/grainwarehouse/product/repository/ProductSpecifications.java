package com.grainwarehouse.product.repository;

import com.grainwarehouse.common.search.CommonSpecifications;
import com.grainwarehouse.pricelist.entity.PriceListItem;
import com.grainwarehouse.product.dto.ProductSearchCriteria;
import com.grainwarehouse.product.entity.Product;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/** Ghép tiêu chí lọc sản phẩm thành điều kiện SQL; thêm tiêu chí mới = thêm một dòng ở đây. */
public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> from(ProductSearchCriteria criteria) {
        return Specification.allOf(
                CommonSpecifications.keywordContains(criteria, "code", "name"),
                CommonSpecifications.equal("category.id", criteria.categoryId()),
                CommonSpecifications.hasActive(criteria),
                hasSalePrice(criteria.hasSalePrice()));
    }

    /**
     * Khi có {@code prioritizePriceListId}: bỏ sort của Pageable để {@link #from(ProductSearchCriteria, Sort)}
     * tự đặt ORDER BY (SP đã có giá trong bảng giá đó đứng trước, rồi mới theo sort gốc).
     * Lý do: Spring Data JPA ghi đè {@code query.orderBy(...)} của Specification nếu Pageable còn mang Sort.
     */
    public static Pageable toPageable(ProductSearchCriteria criteria, Pageable pageable) {
        if (criteria.prioritizePriceListId() == null) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    }

    public static Specification<Product> from(ProductSearchCriteria criteria, Sort sort) {
        return Specification.allOf(
                from(criteria),
                prioritizePriceList(criteria.prioritizePriceListId(), sort));
    }

    private static Specification<Product> hasSalePrice(Boolean hasSalePrice) {
        if (hasSalePrice == null) {
            return null;
        }
        return (root, query, cb) -> hasSalePrice
                ? cb.isNotNull(root.get("salePrice"))
                : cb.isNull(root.get("salePrice"));
    }

    private static Specification<Product> prioritizePriceList(Long priceListId, Sort sort) {
        if (priceListId == null) {
            return null;
        }
        return (root, query, cb) -> {
            Class<?> resultType = query.getResultType();
            if (resultType != Long.class && resultType != long.class) {
                Subquery<Long> sub = query.subquery(Long.class);
                Root<PriceListItem> item = sub.from(PriceListItem.class);
                sub.select(cb.literal(1L))
                        .where(cb.equal(item.get("priceList").get("id"), priceListId),
                                cb.equal(item.get("product"), root));
                Expression<Integer> hasPriceRank = cb.<Integer>selectCase()
                        .when(cb.exists(sub), 0)
                        .otherwise(1);
                var orders = new java.util.ArrayList<jakarta.persistence.criteria.Order>();
                orders.add(cb.asc(hasPriceRank));
                for (Sort.Order order : sort) {
                    orders.add(order.isAscending()
                            ? cb.asc(root.get(order.getProperty()))
                            : cb.desc(root.get(order.getProperty())));
                }
                if (sort.isUnsorted()) {
                    orders.add(cb.asc(root.get("id")));
                }
                query.orderBy(orders);
            }
            return null;
        };
    }
}
