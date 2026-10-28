package com.grainwarehouse.inventory.dto;

import com.grainwarehouse.customer.entity.Customer;
import com.grainwarehouse.inventory.entity.StockDocument;
import com.grainwarehouse.inventory.entity.StockDocumentType;
import com.grainwarehouse.inventory.entity.StockTransaction;
import com.grainwarehouse.inventory.entity.StockTransactionType;
import com.grainwarehouse.supplier.entity.Supplier;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Một dòng lịch sử biến động tồn: SP, kho, chiều, số lượng, tồn trước/sau TẠI KHO ĐÓ và chứng từ gây ra.
 * reversalOfId có giá trị = giao dịch đảo (BR-INV-06). supplierXxx chỉ có với phiếu nhập, customerXxx với phiếu bán
 * (null bị bỏ khỏi JSON).
 */
public record StockTransactionResponse(
        Long id,
        Instant createdAt,
        StockTransactionType type,
        int quantity,
        int quantityBefore,
        int quantityAfter,
        Long reversalOfId,
        Long productId,
        String productCode,
        String productName,
        String unitName,
        Long warehouseId,
        String warehouseCode,
        String warehouseName,
        Long documentId,
        String documentCode,
        StockDocumentType documentType,
        LocalDate documentDate,
        Long supplierId,
        String supplierName,
        Long customerId,
        String customerName) {

    public static StockTransactionResponse from(StockTransaction transaction) {
        StockDocument document = transaction.getDocumentItem().getDocument();
        Supplier supplier = document.getSupplier();
        Customer customer = document.getCustomer();
        return new StockTransactionResponse(
                transaction.getId(),
                transaction.getCreatedAt(),
                transaction.getType(),
                transaction.getQuantity(),
                transaction.getQuantityBefore(),
                transaction.getQuantityAfter(),
                // Lấy id từ proxy LAZY không sinh thêm truy vấn
                transaction.getReversalOf() == null ? null : transaction.getReversalOf().getId(),
                transaction.getProduct().getId(),
                transaction.getProduct().getCode(),
                transaction.getProduct().getName(),
                transaction.getProduct().getUnit().getName(),
                transaction.getWarehouse().getId(),
                transaction.getWarehouse().getCode(),
                transaction.getWarehouse().getName(),
                document.getId(),
                document.getCode(),
                document.getType(),
                document.getDocumentDate(),
                supplier == null ? null : supplier.getId(),
                supplier == null ? null : supplier.getName(),
                customer == null ? null : customer.getId(),
                customer == null ? null : customer.getName());
    }
}
