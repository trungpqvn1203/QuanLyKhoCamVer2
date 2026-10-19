package com.grainwarehouse.inventory.repository;

import com.grainwarehouse.inventory.entity.StockTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Chỉ thêm và đọc — không có hàm sửa/xóa nào được dùng (BR-INV-05, trigger ở DB chặn nếu lỡ gọi). */
public interface StockTransactionRepository extends JpaRepository<StockTransaction, Long> {

    /**
     * Giao dịch gốc (không phải giao dịch đảo) của một chứng từ, theo thứ tự product_id tăng dần —
     * cùng thứ tự khóa dòng tồn với lúc hoàn tất phiếu để giảm nguy cơ deadlock.
     */
    @Query("""
            select t from StockTransaction t
            join fetch t.documentItem i
            join fetch t.product
            join fetch t.warehouse
            where i.document.id = :documentId and t.reversalOf is null
            order by t.product.id
            """)
    List<StockTransaction> findOriginalsOfDocument(@Param("documentId") Long documentId);

    @Query("select t from StockTransaction t where t.documentItem.document.id = :documentId order by t.id")
    List<StockTransaction> findByDocumentId(@Param("documentId") Long documentId);
}
