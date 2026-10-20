package com.grainwarehouse.sales.controller;

import com.grainwarehouse.common.response.ApiResponse;
import com.grainwarehouse.sales.dto.CustomerPriceRequest;
import com.grainwarehouse.sales.dto.CustomerPriceResponse;
import com.grainwarehouse.sales.service.CustomerPriceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Bảng giá riêng của một khách (BR-SAL-06). PUT theo productId = đặt hoặc ghi đè giá (idempotent). */
@RestController
@RequestMapping("/api/customers/{customerId}/prices")
@RequiredArgsConstructor
public class CustomerPriceController {

    private final CustomerPriceService customerPriceService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CustomerPriceResponse>>> list(@PathVariable Long customerId) {
        return ResponseEntity.ok(ApiResponse.ok(customerPriceService.list(customerId)));
    }

    @PutMapping("/{productId}")
    public ResponseEntity<ApiResponse<List<CustomerPriceResponse>>> set(
            @PathVariable Long customerId, @PathVariable Long productId,
            @Valid @RequestBody CustomerPriceRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(customerPriceService.set(customerId, productId, request.unitPrice()),
                "Lưu giá riêng thành công"));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> remove(@PathVariable Long customerId, @PathVariable Long productId) {
        customerPriceService.remove(customerId, productId);
        return ResponseEntity.noContent().build();
    }
}
