package com.grainwarehouse.delivery.dto;

import com.grainwarehouse.customer.entity.Customer;
import com.grainwarehouse.inventory.entity.StockDocument;
import com.grainwarehouse.inventory.entity.StockDocumentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Một phiếu bán nhìn từ phía giao hàng: điểm giao của chuyến (có stopOrder) hoặc phiếu chờ xếp chuyến (không có).
 * Địa chỉ lấy từ hồ sơ khách (BR-DLV-08); giao nơi khác thì ghi ở ghi chú phiếu bán.
 * documentStatus cho biết phiếu đã bị hủy / hoàn tất riêng / đảo sau khi xếp (BR-DLV-07, 10).
 */
public record DeliveryOrderResponse(
        Integer stopOrder,
        Long documentId,
        String documentCode,
        StockDocumentStatus documentStatus,
        LocalDate documentDate,
        LocalDate requestedDeliveryDate,
        Long customerId,
        String customerCode,
        String customerName,
        String customerPhone,
        String customerAddress,
        Long warehouseId,
        String warehouseName,
        String note,
        BigDecimal totalTons,
        List<DeliveryOrderLineResponse> lines) {

    public static DeliveryOrderResponse from(Integer stopOrder, StockDocument document,
                                             List<DeliveryOrderLineResponse> lines) {
        Customer customer = document.getCustomer();
        return new DeliveryOrderResponse(
                stopOrder,
                document.getId(),
                document.getCode(),
                document.getStatus(),
                document.getDocumentDate(),
                document.getRequestedDeliveryDate(),
                customer.getId(),
                customer.getCode(),
                customer.getName(),
                customer.getPhone(),
                customer.getAddress(),
                document.getWarehouse() == null ? null : document.getWarehouse().getId(),
                document.getWarehouse() == null ? null : document.getWarehouse().getName(),
                document.getNote(),
                lines.stream().map(DeliveryOrderLineResponse::tons).reduce(BigDecimal.ZERO, BigDecimal::add),
                lines);
    }
}
