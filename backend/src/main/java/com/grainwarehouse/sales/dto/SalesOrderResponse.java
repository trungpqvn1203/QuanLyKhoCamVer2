package com.grainwarehouse.sales.dto;

import com.grainwarehouse.inventory.entity.StockDocument;
import com.grainwarehouse.inventory.entity.StockDocumentStatus;
import com.grainwarehouse.sales.entity.OrderChannel;
import com.grainwarehouse.sales.entity.SalePayment;
import com.grainwarehouse.sales.entity.SalePriceAdjustment;
import com.grainwarehouse.sales.entity.SaleRequestItem;
import com.grainwarehouse.warehouse.entity.Warehouse;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Chi tiết phiếu bán kèm dòng hàng, phần khách đặt và lịch sử thanh toán. Trường null bị bỏ khỏi JSON —
 * phiếu Chờ xác nhận chưa có kho nên không có warehouseId/Code/Name (BR-SAL-17).
 */
public record SalesOrderResponse(
        Long id,
        String code,
        StockDocumentStatus status,
        OrderChannel orderChannel,
        LocalDate documentDate,
        LocalDate requestedDeliveryDate,
        Long warehouseId,
        String warehouseCode,
        String warehouseName,
        Long customerId,
        String customerCode,
        String customerName,
        String customerPhone,
        // Ghi chú nội bộ — app khách KHÔNG BAO GIỜ trả trường này (BR-POR-11)
        String note,
        // Lời khách ghi trên app (chỉ đơn kênh APP) — nhân viên chỉ đọc
        String customerNote,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal outstandingAmount,
        // Tổng tấn = cộng tấn từng dòng (đã làm tròn) → khớp với số in trên từng dòng
        BigDecimal totalTons,
        String reason,
        String createdBy,
        Instant confirmedAt,
        String confirmedBy,
        Instant completedAt,
        Instant cancelledAt,
        Instant reversedAt,
        Instant createdAt,
        Instant updatedAt,
        List<SalesOrderLineResponse> lines,
        // BR-SAL-20: khách đặt gì — rỗng với phiếu kho lập trực tiếp
        List<SaleRequestLineResponse> requestLines,
        List<SalePaymentResponse> payments,
        // BR-SAL-26: lịch sử điều chỉnh giá sau khi giao — rỗng nếu chưa từng điều chỉnh
        List<SalePriceAdjustmentResponse> priceAdjustments) {

    public static SalesOrderResponse from(StockDocument document, List<SaleRequestItem> requestItems,
                                          List<SalePayment> payments, List<SalePriceAdjustment> adjustments) {
        List<SalesOrderLineResponse> lines = document.getItems().stream().map(SalesOrderLineResponse::from).toList();
        Warehouse warehouse = document.getWarehouse();
        Set<Long> reversedIds = payments.stream()
                .map(SalePayment::getReversalOf)
                .filter(Objects::nonNull)
                .map(SalePayment::getId)
                .collect(Collectors.toSet());
        return new SalesOrderResponse(
                document.getId(),
                document.getCode(),
                document.getStatus(),
                document.getOrderChannel(),
                document.getDocumentDate(),
                document.getRequestedDeliveryDate(),
                warehouse == null ? null : warehouse.getId(),
                warehouse == null ? null : warehouse.getCode(),
                warehouse == null ? null : warehouse.getName(),
                document.getCustomer().getId(),
                document.getCustomer().getCode(),
                document.getCustomer().getName(),
                document.getCustomer().getPhone(),
                document.getNote(),
                document.getCustomerNote(),
                document.getTotalAmount(),
                document.getPaidAmount(),
                document.getOutstandingAmount(),
                lines.stream().map(SalesOrderLineResponse::tons).reduce(BigDecimal.ZERO, BigDecimal::add),
                document.getReason(),
                document.getCreatedBy(),
                document.getConfirmedAt(),
                document.getConfirmedBy(),
                document.getCompletedAt(),
                document.getCancelledAt(),
                document.getReversedAt(),
                document.getCreatedAt(),
                document.getUpdatedAt(),
                lines,
                requestItems.stream().map(SaleRequestLineResponse::from).toList(),
                payments.stream().map(payment -> SalePaymentResponse.from(payment, reversedIds)).toList(),
                adjustments.stream().map(SalePriceAdjustmentResponse::from).toList());
    }
}
