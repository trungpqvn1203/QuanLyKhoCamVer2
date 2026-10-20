package com.grainwarehouse.inventory.service;

import com.grainwarehouse.common.response.PageResponse;
import com.grainwarehouse.inventory.dto.StockTransactionResponse;
import com.grainwarehouse.inventory.dto.StockTransactionSearchCriteria;
import com.grainwarehouse.inventory.repository.StockTransactionRepository;
import com.grainwarehouse.inventory.repository.StockTransactionSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lịch sử biến động tồn — chỉ đọc; giao dịch chỉ được ghi bởi các service chứng từ (BR-INV-04, 05). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockTransactionService {

    private final StockTransactionRepository stockTransactionRepository;

    public PageResponse<StockTransactionResponse> list(StockTransactionSearchCriteria criteria, Pageable pageable) {
        return PageResponse.from(
                stockTransactionRepository.findAll(StockTransactionSpecifications.from(criteria), pageable),
                StockTransactionResponse::from);
    }
}
