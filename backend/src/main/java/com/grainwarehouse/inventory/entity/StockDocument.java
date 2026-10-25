package com.grainwarehouse.inventory.entity;

import com.grainwarehouse.common.entity.AuditableEntity;
import com.grainwarehouse.customer.entity.Customer;
import com.grainwarehouse.supplier.entity.Supplier;
import com.grainwarehouse.warehouse.entity.Warehouse;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * Chứng từ kho dùng chung cho phiếu nhập / xuất / điều chuyển, phân biệt bằng {@link #type}.
 * Entity KHÔNG tự kiểm tra trạng thái: mọi thay đổi phải đi qua service, sau khi đã khóa dòng
 * (SELECT ... FOR UPDATE) và kiểm tra lại trạng thái bên trong transaction (BR-IMP-14).
 * Vì vậy không có setStatus — chỉ có các hàm markXxx tương ứng ba bước chuyển hợp lệ.
 */
@Getter
@Entity
@Table(name = "stock_document")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockDocument extends AuditableEntity {

    // Service sinh mã trước khi lưu (BR-IMP-07); không sửa được sau đó
    @Column(nullable = false, length = 20, updatable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private StockDocumentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StockDocumentStatus status = StockDocumentStatus.DRAFT;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    // Chỉ phiếu bán (BR-SAL-01); CHECK ck_stock_document_customer ở DB
    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Setter
    @Column(name = "document_date", nullable = false)
    private LocalDate documentDate;

    @Setter
    @Column(length = 500)
    private String note;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 0)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    // Tổng đã thanh toán (snapshot): phiếu bán = đã thu (sale_payment, BR-SAL-10),
    // phiếu nhập = đã trả NCC (supplier_payment, BR-SPAY-01)
    @Column(name = "paid_amount", nullable = false, precision = 18, scale = 0)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    // BR-SPAY-05: false = phiếu nhập hoàn tất trước khi có công nợ NCC, coi như đã trả đủ (V16). Chỉ migration đặt false
    @Setter(AccessLevel.NONE)
    @Column(name = "tracks_debt", nullable = false)
    private boolean tracksDebt = true;

    // Lý do hủy hoặc đảo
    @Column(length = 500)
    private String reason;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    // orphanRemoval: bỏ dòng khỏi list = DELETE dòng đó khi flush (chỉ xảy ra với phiếu nháp)
    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<StockDocumentItem> items = new ArrayList<>();

    public StockDocument(StockDocumentType type, String code) {
        this.type = type;
        this.code = code;
    }

    /** Chỉ đọc: thêm/bớt dòng qua addItem/removeItemsIf để giữ quan hệ hai chiều đúng. */
    public List<StockDocumentItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public void addItem(StockDocumentItem item) {
        item.attachTo(this);
        items.add(item);
    }

    public void removeItemsIf(Predicate<StockDocumentItem> condition) {
        items.removeIf(condition);
    }

    /** BR-MON-05: tổng phiếu = tổng thành tiền các dòng (mỗi dòng đã làm tròn), không làm tròn thêm. */
    public void recalculateTotal() {
        totalAmount = items.stream().map(StockDocumentItem::getLineAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * BR-SAL-10, BR-SPAY-01: số còn nợ = tổng phiếu − đã thanh toán, CHỈ với phiếu Hoàn tất. Nháp chưa giao,
     * Đã hủy không giao, Đã đảo thì hàng đã trả lại — cả ba đều không phát sinh nợ (trước đây phiếu Đã đảo hiện
     * nhầm "còn nợ 620.000 đ"). Phiếu nhập cũ không theo dõi công nợ (BR-SPAY-05) cũng không còn nợ.
     */
    public BigDecimal getOutstandingAmount() {
        return status == StockDocumentStatus.COMPLETED && tracksDebt
                ? totalAmount.subtract(paidAmount) : BigDecimal.ZERO;
    }

    /**
     * Cộng (thu) hoặc trừ (hoàn) vào tổng đã thu. Không tự kiểm tra vượt nợ — service kiểm tra sau khi
     * khóa phiếu; CHECK ck_stock_document_paid ở DB là tuyến phòng thủ cuối.
     */
    public void addPaidAmount(BigDecimal delta) {
        paidAmount = paidAmount.add(delta);
    }

    public void markCompleted(Instant at) {
        status = StockDocumentStatus.COMPLETED;
        completedAt = at;
    }

    public void markCancelled(String reason, Instant at) {
        status = StockDocumentStatus.CANCELLED;
        this.reason = reason;
        cancelledAt = at;
    }

    public void markReversed(String reason, Instant at) {
        status = StockDocumentStatus.REVERSED;
        this.reason = reason;
        reversedAt = at;
    }
}
