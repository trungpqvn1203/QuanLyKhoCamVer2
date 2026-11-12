package com.grainwarehouse.inventory.dto;

import com.grainwarehouse.customer.entity.Customer;
import com.grainwarehouse.inventory.entity.StockDocument;
import com.grainwarehouse.inventory.entity.StockDocumentType;
import com.grainwarehouse.inventory.entity.StockTransaction;
import com.grainwarehouse.inventory.entity.StockTransactionType;
import com.grainwarehouse.supplier.entity.Supplier;
import com.grainwarehouse.warehouse.entity.Warehouse;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Một dòng lịch sử biến động tồn: SP, kho, chiều, số lượng, tồn trước/sau TẠI KHO ĐÓ và chứng từ gây ra.
 * reversalOfId có giá trị = giao dịch đảo (BR-INV-06). supplierXxx chỉ có với phiếu nhập, customerXxx với phiếu bán
 * (null bị bỏ khỏi JSON). counterpartWarehouseXxx chỉ có với phiếu điều chuyển: kho ở ĐẦU KIA của lần chuyển — dòng
 * tại kho nguồn ghi kho đích và ngược lại, để đọc được "chuyển đi đâu / từ đâu về" ngay trên một dòng lịch sử.
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
        String customerName,
        Long counterpartWarehouseId,
        String counterpartWarehouseName) {

    public static StockTransactionResponse from(StockTransaction transaction) {
        StockDocument document = transaction.getDocumentItem().getDocument();
        Supplier supplier = document.getSupplier();
        Customer customer = document.getCustomer();
        Warehouse counterpart = counterpartWarehouse(document, transaction.getWarehouse());
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
                customer == null ? null : customer.getName(),
                counterpart == null ? null : counterpart.getId(),
                counterpart == null ? null : counterpart.getName());
    }

    private static Warehouse counterpartWarehouse(StockDocument document, Warehouse here) {
        if (document.getType() != StockDocumentType.TRANSFER) {
            return null;
        }
        return here.getId().equals(document.getWarehouse().getId())
                ? document.getTargetWarehouse() : document.getWarehouse();
    }
}
