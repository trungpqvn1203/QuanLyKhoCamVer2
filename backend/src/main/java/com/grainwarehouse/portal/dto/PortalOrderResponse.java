package com.grainwarehouse.portal.dto;

import com.grainwarehouse.inventory.entity.StockDocumentStatus;
import com.grainwarehouse.sales.dto.SaleRequestLineResponse;
import com.grainwarehouse.sales.dto.SalesOrderResponse;
import com.grainwarehouse.sales.entity.OrderChannel;
import com.grainwarehouse.sales.service.SalesOrderService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static com.grainwarehouse.inventory.entity.StockDocumentStatus.CANCELLED;
import static com.grainwarehouse.inventory.entity.StockDocumentStatus.COMPLETED;
import static com.grainwarehouse.inventory.entity.StockDocumentStatus.DRAFT;
import static com.grainwarehouse.inventory.entity.StockDocumentStatus.PENDING;
import static com.grainwarehouse.inventory.entity.StockDocumentStatus.REVERSED;

/**
 * Chi tiết đơn khách thấy trên app (BR-POR-08). Cố ý chỉ chép những trường được phép ra khỏi SalesOrderResponse:
 * không kho xuất, không người lập / xác nhận, không đã thu / còn nợ, không lịch sử thanh toán.
 * BR-POR-11: note = LỜI KHÁCH (customerNote), không bao giờ là ghi chú nội bộ; lý do hủy chỉ hiện nguyên văn khi khách
 * tự hủy — cửa hàng hủy / hủy giao thì hiện câu chung, lý do nội bộ không ra khỏi cửa hàng.
 * Giá (lines, totalAmount) chỉ có khi cửa hàng đã xác nhận (Nháp / Hoàn tất); trường null bị bỏ khỏi JSON.
 */
public record PortalOrderResponse(
        Long id,
        String code,
        StockDocumentStatus status,
        OrderChannel orderChannel,
        LocalDate orderDate,
        LocalDate requestedDeliveryDate,
        String note,
        Instant createdAt,
        // Sửa / tự hủy được (BR-POR-09)
        boolean editable,
        List<SaleRequestLineResponse> requestLines,
        List<PortalOrderLineResponse> lines,
        BigDecimal totalAmount,
        String cancelReason) {

    private static final Set<StockDocumentStatus> PRICED = Set.of(DRAFT, COMPLETED);
    static final String STORE_CANCELLED = "Cửa hàng đã hủy đơn này. Vui lòng liên hệ cửa hàng nếu cần";
    static final String STORE_REVERSED = "Cửa hàng đã hủy giao đơn này. Vui lòng liên hệ cửa hàng nếu cần";

    public static PortalOrderResponse from(SalesOrderResponse order) {
        boolean priced = PRICED.contains(order.status());
        boolean cancelled = order.status() == CANCELLED || order.status() == REVERSED;
        return new PortalOrderResponse(
                order.id(),
                order.code(),
                order.status(),
                order.orderChannel(),
                order.documentDate(),
                order.requestedDeliveryDate(),
                order.customerNote(),
                order.createdAt(),
                order.status() == PENDING,
                order.requestLines(),
                priced ? order.lines().stream().map(PortalOrderLineResponse::from).toList() : List.of(),
                priced ? order.totalAmount() : null,
                cancelled ? customerFacingReason(order) : null);
    }

    private static String customerFacingReason(SalesOrderResponse order) {
        if (order.status() == REVERSED) {
            return STORE_REVERSED;
        }
        String reason = order.reason();
        return reason != null && reason.startsWith(SalesOrderService.APP_CANCEL_REASON) ? reason : STORE_CANCELLED;
    }
}
