package com.grainwarehouse.sales.service;

import com.grainwarehouse.customer.entity.Customer;
import com.grainwarehouse.customer.repository.CustomerRepository;
import com.grainwarehouse.inventory.repository.CustomerDebtRow;
import com.grainwarehouse.inventory.repository.StockDocumentRepository;
import com.grainwarehouse.sales.dto.CustomerDebtResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Công nợ khách hàng (BR-SAL-10): tính từ paid_amount của phiếu bán, không có số dư nợ lưu riêng.
 * Chỉ liệt kê khách đang còn nợ, nợ nhiều nhất lên đầu.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerDebtService {

    private final StockDocumentRepository stockDocumentRepository;
    private final CustomerRepository customerRepository;

    public List<CustomerDebtResponse> list(Long customerId) {
        List<CustomerDebtRow> rows = stockDocumentRepository.sumOutstandingByCustomer(customerId);
        // Một câu IN cho mọi khách thay vì một câu mỗi khách (N+1)
        Map<Long, Customer> customers = customerRepository
                .findAllById(rows.stream().map(CustomerDebtRow::getCustomerId).toList()).stream()
                .collect(Collectors.toMap(Customer::getId, Function.identity()));
        return rows.stream()
                .map(row -> toResponse(row, customers.get(row.getCustomerId())))
                .sorted(Comparator.comparing(CustomerDebtResponse::outstandingAmount).reversed()
                        .thenComparing(CustomerDebtResponse::customerCode))
                .toList();
    }

    private static CustomerDebtResponse toResponse(CustomerDebtRow row, Customer customer) {
        return new CustomerDebtResponse(
                customer.getId(),
                customer.getCode(),
                customer.getName(),
                customer.getType(),
                customer.getPhone(),
                customer.isActive(),
                row.getOutstandingAmount(),
                row.getOpenOrderCount(),
                row.getOldestDocumentDate());
    }
}
