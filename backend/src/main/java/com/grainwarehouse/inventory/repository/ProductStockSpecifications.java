package com.grainwarehouse.inventory.repository;

import com.grainwarehouse.inventory.dto.ProductStockSearchCriteria;
import com.grainwarehouse.inventory.dto.StockStatus;
import com.grainwarehouse.inventory.entity.Inventory;
import com.grainwarehouse.product.dto.ProductSearchCriteria;
import com.grainwarehouse.product.entity.Product;
import com.grainwarehouse.product.repository.ProductSpecifications;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

/**
 * Lọc SP kèm điều kiện tồn. Dùng lại nguyên điều kiện của ProductSpecifications (keyword, danh mục, trạng thái)
 * → hai danh sách lọc giống hệt nhau. Nằm ở module inventory vì cần bảng inventory; inventory đã phụ thuộc
 * product nên chiều phụ thuộc vẫn một chiều.
 */
public final class ProductStockSpecifications {

    private ProductStockSpecifications() {
    }

    public static Specification<Product> from(ProductStockSearchCriteria criteria) {
        return Specification.allOf(
                ProductSpecifications.from(
                        new ProductSearchCriteria(criteria.keyword(), criteria.categoryId(), criteria.active())),
                hasStockStatus(criteria.stockStatus()));
    }

    /**
     * Cùng quy tắc với StockStatus.of, viết bằng SQL để lọc và phân trang đúng:
     * (SELECT COALESCE(SUM(quantity), 0) FROM inventory WHERE product_id = p.id) so với p.min_stock_quantity.
     * Luôn tính trên tổng mọi kho (BR-STK-04), không phụ thuộc warehouseId.
     */
    static Specification<Product> hasStockStatus(StockStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> {
            Subquery<Long> total = query.subquery(Long.class);
            Root<Inventory> inventory = total.from(Inventory.class);
            total.select(cb.coalesce(cb.sumAsLong(inventory.get("quantity")), 0L))
                    .where(cb.equal(inventory.get("product"), root));
            Expression<Long> min = root.<Integer>get("minStockQuantity").as(Long.class);

            return switch (status) {
                case OUT -> cb.equal(total, 0L);
                case LOW -> cb.and(cb.greaterThan(total, 0L), cb.isNotNull(min), cb.lessThanOrEqualTo(total, min));
                case IN_STOCK -> cb.and(cb.greaterThan(total, 0L), cb.or(cb.isNull(min), cb.greaterThan(total, min)));
            };
        };
    }
}
