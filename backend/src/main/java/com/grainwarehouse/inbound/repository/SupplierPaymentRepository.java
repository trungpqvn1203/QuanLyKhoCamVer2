package com.grainwarehouse.inbound.repository;

import com.grainwarehouse.inbound.entity.SupplierPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Chỉ thêm và đọc — không sửa/xóa (BR-SPAY-03, trigger ở DB chặn nếu lỡ gọi). */
public interface SupplierPaymentRepository extends JpaRepository<SupplierPayment, Long> {

    @Query("select p from SupplierPayment p left join fetch p.reversalOf where p.document.id = :documentId order by p.id")
    List<SupplierPayment> findByDocumentId(@Param("documentId") Long documentId);

    // Lọc theo phiếu để /api/inbound-receipts/{id}/payments/{paymentId} không chạm được khoản trả của phiếu khác
    Optional<SupplierPayment> findByIdAndDocumentId(Long id, Long documentId);

    boolean existsByReversalOfId(Long reversalOfId);
}
