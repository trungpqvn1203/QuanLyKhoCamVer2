package com.grainwarehouse.inbound.service;

import com.grainwarehouse.inbound.dto.SupplierDebtResponse;
import com.grainwarehouse.inventory.repository.StockDocumentRepository;
import com.grainwarehouse.inventory.repository.SupplierDebtRow;
import com.grainwarehouse.supplier.entity.Supplier;
import com.grainwarehouse.supplier.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Công nợ nhà cung cấp (BR-SPAY-01): tính từ paid_amount của phiếu nhập, không có số dư nợ lưu riêng —
 * cùng cách với công nợ khách (CustomerDebtService). Chỉ liệt kê NCC đang còn nợ, nợ nhiều nhất lên đầu.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupplierDebtService {

    private final StockDocumentRepository stockDocumentRepository;
    private final SupplierRepository supplierRepository;

    public List<SupplierDebtResponse> list(Long supplierId) {
        List<SupplierDebtRow> rows = stockDocumentRepository.sumOutstandingBySupplier(supplierId);
        // Một câu IN cho mọi NCC thay vì một câu mỗi NCC (N+1)
        Map<Long, Supplier> suppliers = supplierRepository
                .findAllById(rows.stream().map(SupplierDebtRow::getSupplierId).toList()).stream()
                .collect(Collectors.toMap(Supplier::getId, Function.identity()));
        return rows.stream()
                .map(row -> toResponse(row, suppliers.get(row.getSupplierId())))
                .sorted(Comparator.comparing(SupplierDebtResponse::outstandingAmount).reversed()
                        .thenComparing(SupplierDebtResponse::supplierCode))
                .toList();
    }

    private static SupplierDebtResponse toResponse(SupplierDebtRow row, Supplier supplier) {
        return new SupplierDebtResponse(
                supplier.getId(),
                supplier.getCode(),
                supplier.getName(),
                supplier.getTaxCode(),
                supplier.getPhone(),
                supplier.isActive(),
                row.getOutstandingAmount(),
                row.getOpenReceiptCount(),
                row.getOldestDocumentDate());
    }
}
