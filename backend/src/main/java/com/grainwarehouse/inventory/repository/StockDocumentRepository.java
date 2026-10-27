package com.grainwarehouse.inventory.repository;

import com.grainwarehouse.inventory.entity.StockDocument;
import com.grainwarehouse.inventory.entity.StockDocumentType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockDocumentRepository
        extends JpaRepository<StockDocument, Long>, JpaSpecificationExecutor<StockDocument> {

    /**
     * SELECT ... FOR UPDATE: giữ khóa dòng phiếu đến hết transaction. Transaction khác gọi hàm này
     * với cùng phiếu sẽ CHỜ, rồi đọc được trạng thái mới nhất (BR-IMP-14).
     * Lọc theo type để /api/inbound-receipts/{id} không thao tác được trên phiếu loại khác.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from StockDocument d where d.id = :id and d.type = :type")
    Optional<StockDocument> findForUpdate(@Param("id") Long id, @Param("type") StockDocumentType type);

    // Màn chi tiết: JOIN lấy luôn kho, NCC/khách, các dòng và SP của từng dòng — tránh N+1
    @EntityGraph(attributePaths = {"warehouse", "supplier", "customer", "items", "items.product"})
    Optional<StockDocument> findWithDetailsByIdAndType(Long id, StockDocumentType type);

    // Danh sách: chỉ cần kho + NCC/khách (không tải dòng hàng)
    @Override
    @EntityGraph(attributePaths = {"warehouse", "supplier", "customer"})
    Page<StockDocument> findAll(Specification<StockDocument> spec, Pageable pageable);

    // BR-IMP-07: sequence an toàn khi nhiều request tạo phiếu đồng thời (không bao giờ cấp trùng số)
    @Query(value = "SELECT nextval('inbound_code_seq')", nativeQuery = true)
    long nextInboundCodeNumber();

    // BR-SAL-03: mã phiếu bán PX
    @Query(value = "SELECT nextval('outbound_code_seq')", nativeQuery = true)
    long nextOutboundCodeNumber();

    // BR-OPN-01: mã phiếu tồn đầu kỳ DTK
    @Query(value = "SELECT nextval('opening_stock_code_seq')", nativeQuery = true)
    long nextOpeningStockCodeNumber();

    /**
     * BR-SAL-10: công nợ theo khách = tổng (tổng phiếu − đã thu) của phiếu bán Hoàn tất còn nợ.
     * Phiếu Đã đảo không tính (đảo chỉ được khi chưa thu đồng nào, BR-SAL-14).
     */
    @Query("""
            select d.customer.id as customerId, sum(d.totalAmount - d.paidAmount) as outstandingAmount,
                   count(d) as openOrderCount, min(d.documentDate) as oldestDocumentDate
            from StockDocument d
            where d.type = com.grainwarehouse.inventory.entity.StockDocumentType.OUTBOUND
              and d.status = com.grainwarehouse.inventory.entity.StockDocumentStatus.COMPLETED
              and d.paidAmount < d.totalAmount
              and (:customerId is null or d.customer.id = :customerId)
            group by d.customer.id
            """)
    List<CustomerDebtRow> sumOutstandingByCustomer(@Param("customerId") Long customerId);

    /**
     * BR-SPAY-01: công nợ theo NCC = tổng (tổng phiếu − đã trả) của phiếu nhập Hoàn tất còn nợ.
     * Bỏ phiếu cũ không theo dõi công nợ (BR-SPAY-05) và phiếu Đã đảo (đảo chỉ được khi chưa trả, BR-SPAY-04).
     */
    @Query("""
            select d.supplier.id as supplierId, sum(d.totalAmount - d.paidAmount) as outstandingAmount,
                   count(d) as openReceiptCount, min(d.documentDate) as oldestDocumentDate
            from StockDocument d
            where d.type = com.grainwarehouse.inventory.entity.StockDocumentType.INBOUND
              and d.status = com.grainwarehouse.inventory.entity.StockDocumentStatus.COMPLETED
              and d.tracksDebt = true
              and d.paidAmount < d.totalAmount
              and (:supplierId is null or d.supplier.id = :supplierId)
            group by d.supplier.id
            """)
    List<SupplierDebtRow> sumOutstandingBySupplier(@Param("supplierId") Long supplierId);
}
