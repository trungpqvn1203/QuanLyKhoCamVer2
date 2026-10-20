package com.grainwarehouse.sales.repository;

import com.grainwarehouse.sales.entity.CustomerProductPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface CustomerProductPriceRepository extends JpaRepository<CustomerProductPrice, Long> {

    // Bảng giá của một khách — dùng uk_customer_product_price (customer_id đứng đầu)
    @Query("""
            select p from CustomerProductPrice p join fetch p.product
            where p.customer.id = :customerId order by p.product.code
            """)
    List<CustomerProductPrice> findByCustomerId(@Param("customerId") Long customerId);

    /**
     * Đặt giá: chưa có thì thêm, có rồi thì ghi đè — một câu SQL, nên hai người cùng đặt giá lần đầu
     * cho cùng cặp (khách, SP) không bị lỗi trùng UNIQUE (cùng kỹ thuật với InventoryRepository.increase).
     * clearAutomatically: câu SQL viết tay đi thẳng xuống DB, bỏ qua persistence context — không xóa context
     * thì đọc lại bảng giá trong cùng transaction vẫn thấy giá CŨ đang nằm trong bộ nhớ.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO customer_product_price (customer_id, product_id, unit_price)
            VALUES (:customerId, :productId, :unitPrice)
            ON CONFLICT (customer_id, product_id)
            DO UPDATE SET unit_price = EXCLUDED.unit_price, updated_at = now()
            """, nativeQuery = true)
    void upsert(@Param("customerId") Long customerId, @Param("productId") Long productId,
                @Param("unitPrice") BigDecimal unitPrice);

    @Modifying
    @Query("delete from CustomerProductPrice p where p.customer.id = :customerId and p.product.id = :productId")
    int deleteByCustomerIdAndProductId(@Param("customerId") Long customerId, @Param("productId") Long productId);
}
