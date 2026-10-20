package com.grainwarehouse.sales.repository;

import com.grainwarehouse.sales.entity.SalePayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Chỉ thêm và đọc — không có hàm sửa/xóa nào được dùng (BR-SAL-13, trigger ở DB chặn nếu lỡ gọi). */
public interface SalePaymentRepository extends JpaRepository<SalePayment, Long> {

    @Query("select p from SalePayment p left join fetch p.reversalOf where p.document.id = :documentId order by p.id")
    List<SalePayment> findByDocumentId(@Param("documentId") Long documentId);

    // Lọc theo phiếu để /api/sales-orders/{id}/payments/{paymentId} không chạm được khoản thu của phiếu khác
    Optional<SalePayment> findByIdAndDocumentId(Long id, Long documentId);

    boolean existsByReversalOfId(Long reversalOfId);
}
