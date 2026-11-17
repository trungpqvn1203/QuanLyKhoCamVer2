package com.grainwarehouse.pricelist.controller;

import com.grainwarehouse.common.response.ApiResponse;
import com.grainwarehouse.common.response.PageResponse;
import com.grainwarehouse.pricelist.dto.CreatePriceListRequest;
import com.grainwarehouse.pricelist.dto.PriceListItemRequest;
import com.grainwarehouse.pricelist.dto.PriceListItemResponse;
import com.grainwarehouse.pricelist.dto.PriceListResponse;
import com.grainwarehouse.pricelist.dto.PriceListSearchCriteria;
import com.grainwarehouse.pricelist.dto.UpdatePriceListRequest;
import com.grainwarehouse.pricelist.service.PriceListService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Quản lý nhiều bảng giá bán ("Giá lẻ", "Giá đại lý", "Giá trang trại"...) và giá từng SP trong bảng giá (BR-PRC-01..07). */
@RestController
@RequestMapping("/api/price-lists")
@RequiredArgsConstructor
public class PriceListController {

    private final PriceListService priceListService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PriceListResponse>>> list(
            @ModelAttribute PriceListSearchCriteria criteria,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(priceListService.list(criteria, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PriceListResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(priceListService.get(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PriceListResponse>> create(@Valid @RequestBody CreatePriceListRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(priceListService.create(request), "Tạo bảng giá thành công"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PriceListResponse>> update(
            @PathVariable Long id, @Valid @RequestBody UpdatePriceListRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(priceListService.update(id, request), "Cập nhật bảng giá thành công"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        priceListService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    /** Giá của các SP trong mọi bảng giá. */
    @GetMapping("/items")
    public ResponseEntity<ApiResponse<List<PriceListItemResponse>>> listItemsByProducts(
            @RequestParam(name = "productIds", required = false) List<Long> productIds) {
        return ResponseEntity.ok(ApiResponse.ok(priceListService.listItemsByProducts(productIds)));
    }

    /** Toàn bộ dòng giá của một bảng giá (khi mở chi tiết bảng giá hoặc lập phiếu bán cho khách thuộc bảng giá này). */
    @GetMapping("/{priceListId}/items")
    public ResponseEntity<ApiResponse<List<PriceListItemResponse>>> listItems(@PathVariable Long priceListId) {
        return ResponseEntity.ok(ApiResponse.ok(priceListService.listItems(priceListId)));
    }

    @PutMapping("/{priceListId}/items/{productId}")
    public ResponseEntity<ApiResponse<PriceListItemResponse>> setItemPrice(
            @PathVariable Long priceListId,
            @PathVariable Long productId,
            @Valid @RequestBody PriceListItemRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                priceListService.setItemPrice(priceListId, productId, request.unitPrice()),
                "Lưu giá thành công"));
    }

    @DeleteMapping("/{priceListId}/items/{productId}")
    public ResponseEntity<Void> removeItemPrice(@PathVariable Long priceListId, @PathVariable Long productId) {
        priceListService.removeItemPrice(priceListId, productId);
        return ResponseEntity.noContent().build();
    }
}
