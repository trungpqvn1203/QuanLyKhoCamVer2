package com.grainwarehouse.pricelist.repository;

import com.grainwarehouse.pricelist.entity.PriceListItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PriceListItemRepository extends JpaRepository<PriceListItem, Long> {

    // Giá của một bảng giá — dùng uk_price_list_item (price_list_id đứng đầu)
    @Query("""
            select i from PriceListItem i join fetch i.product
            where i.priceList.id = :priceListId order by i.product.code
            """)
    List<PriceListItem> findByPriceListId(@Param("priceListId") Long priceListId);

    // Giá của một nhóm SP trong mọi bảng giá — dùng idx_price_list_item_product (màn ma trận)
    @Query("select i from PriceListItem i where i.product.id in :productIds")
    List<PriceListItem> findByProductIdIn(@Param("productIds") Collection<Long> productIds);

    @Query("select i from PriceListItem i join fetch i.product where i.priceList.id = :priceListId and i.product.id = :productId")
    Optional<PriceListItem> findOne(@Param("priceListId") Long priceListId, @Param("productId") Long productId);

    /**
     * Đặt giá: chưa có thì thêm, có rồi thì ghi đè — một câu SQL nên hai người cùng đặt giá lần đầu không lỗi
     * UNIQUE (cùng kỹ thuật với CustomerProductPriceRepository.upsert). clearAutomatically: đọc lại trong cùng
     * transaction phải thấy giá MỚI.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO price_list_item (price_list_id, product_id, unit_price)
            VALUES (:priceListId, :productId, :unitPrice)
            ON CONFLICT (price_list_id, product_id)
            DO UPDATE SET unit_price = EXCLUDED.unit_price, updated_at = now()
            """, nativeQuery = true)
    void upsert(@Param("priceListId") Long priceListId, @Param("productId") Long productId,
                @Param("unitPrice") BigDecimal unitPrice);

    @Modifying
    @Query("delete from PriceListItem i where i.priceList.id = :priceListId and i.product.id = :productId")
    int deleteOne(@Param("priceListId") Long priceListId, @Param("productId") Long productId);
}
