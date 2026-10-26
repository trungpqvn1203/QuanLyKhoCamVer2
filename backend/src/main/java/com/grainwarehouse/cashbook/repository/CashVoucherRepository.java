package com.grainwarehouse.cashbook.repository;

import com.grainwarehouse.cashbook.entity.CashVoucher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** Chỉ thêm và đọc — phiếu thu/chi bất biến (BR-CASH-04, trigger ở DB chặn nếu lỡ gọi sửa/xóa). */
public interface CashVoucherRepository extends JpaRepository<CashVoucher, Long>, JpaSpecificationExecutor<CashVoucher> {

    // Danh sách: JOIN lấy luôn danh mục và phiếu gốc (của phiếu hoàn) — tránh N+1
    @Override
    @EntityGraph(attributePaths = {"category", "reversalOf"})
    Page<CashVoucher> findAll(Specification<CashVoucher> spec, Pageable pageable);

    boolean existsByReversalOfId(Long reversalOfId);

    /** Trong các phiếu gốc này, phiếu nào đã có phiếu hoàn — một câu IN cho cả trang (tránh N+1). */
    @Query("select v.reversalOf.id from CashVoucher v where v.reversalOf.id in :ids")
    List<Long> findReversedIds(@Param("ids") Collection<Long> ids);

    // BR-CASH-04: sequence an toàn khi nhiều request lập phiếu đồng thời (không bao giờ cấp trùng số)
    @Query(value = "SELECT nextval('cash_receipt_code_seq')", nativeQuery = true)
    long nextReceiptCodeNumber();

    @Query(value = "SELECT nextval('cash_payment_code_seq')", nativeQuery = true)
    long nextPaymentCodeNumber();
}
