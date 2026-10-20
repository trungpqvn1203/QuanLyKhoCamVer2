package com.grainwarehouse.inventory.repository;

import com.grainwarehouse.inventory.entity.StockTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Chỉ thêm và đọc — không có hàm sửa/xóa nào được dùng (BR-INV-05, trigger ở DB chặn nếu lỡ gọi). */
public interface StockTransactionRepository
        extends JpaRepository<StockTransaction, Long>, JpaSpecificationExecutor<StockTransaction> {

    // Trang lịch sử: JOIN lấy luôn SP, kho, chứng từ, NCC/khách — toàn quan hệ *-to-one nên vẫn phân trang trong SQL, không N+1
    @Override
    @EntityGraph(attributePaths = {"product", "warehouse", "documentItem", "documentItem.document",
            "documentItem.document.supplier", "documentItem.document.customer"})
    Page<StockTransaction> findAll(Specification<StockTransaction> spec, Pageable pageable);

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
