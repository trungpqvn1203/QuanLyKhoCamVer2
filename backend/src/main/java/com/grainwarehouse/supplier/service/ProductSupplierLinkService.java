package com.grainwarehouse.supplier.service;

import com.grainwarehouse.common.exception.ResourceNotFoundException;
import com.grainwarehouse.product.entity.Product;
import com.grainwarehouse.product.service.ProductSupplierLinks;
import com.grainwarehouse.supplier.entity.ProductSupplier;
import com.grainwarehouse.supplier.entity.Supplier;
import com.grainwarehouse.supplier.repository.ProductSupplierRepository;
import com.grainwarehouse.supplier.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Cài đặt {@link ProductSupplierLinks}: gán NCC từ form / file Excel sản phẩm (BR-PRD-10, BR-SUP-05, 06). */
@Service
@RequiredArgsConstructor
// MANDATORY: chỉ chạy bên trong transaction của ProductService / nhập Excel — tạo SP và gán NCC cùng thành công
// hoặc cùng rollback, không bao giờ có SP đã lưu mà liên kết NCC lỗi giữa chừng
@Transactional(propagation = Propagation.MANDATORY)
public class ProductSupplierLinkService implements ProductSupplierLinks {

    private final SupplierRepository supplierRepository;
    private final ProductSupplierRepository productSupplierRepository;

    @Override
    public Optional<Long> findSupplierIdByCode(String code) {
        // Mã tự sinh luôn viết hoa (NCC001) → người dùng gõ "ncc001" vẫn khớp
        return supplierRepository.findByCode(code.trim().toUpperCase(Locale.ROOT)).map(Supplier::getId);
    }

    @Override
    public void validate(Product product, Set<Long> supplierIds) {
        Map<Long, ProductSupplier> existing = existingLinks(product);
        for (Long supplierId : supplierIds) {
            Supplier supplier = supplierRepository.findById(supplierId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà cung cấp id=" + supplierId));
            ProductSupplier link = existing.get(supplierId);
            if (link != null && link.isActive()) {
                // Đã gán từ trước: giữ nguyên dù NCC nay đã ngừng (BR-SUP-07)
                continue;
            }
            SupplierService.checkCanLink(supplier, product);
        }
    }

    @Override
    public void sync(Product product, Set<Long> supplierIds) {
        validate(product, supplierIds);
        Map<Long, ProductSupplier> existing = existingLinks(product);
        for (Long supplierId : supplierIds) {
            ProductSupplier link = existing.get(supplierId);
            if (link == null) {
                productSupplierRepository.save(
                        new ProductSupplier(supplierRepository.getReferenceById(supplierId), product));
            } else {
                link.setActive(true);
            }
        }
        // Bỏ chọn = gỡ (active=false), giữ dòng để bật lại được
        existing.values().stream()
                .filter(link -> link.isActive() && !supplierIds.contains(link.getId().getSupplierId()))
                .forEach(link -> link.setActive(false));
    }

    /** Mọi dòng liên kết (cả đã gỡ) của SP theo supplierId; SP chưa lưu thì chưa có dòng nào. */
    private Map<Long, ProductSupplier> existingLinks(Product product) {
        if (product.getId() == null) {
            return Map.of();
        }
        return productSupplierRepository.findByIdProductId(product.getId()).stream()
                .collect(Collectors.toMap(link -> link.getId().getSupplierId(), Function.identity()));
    }
}
