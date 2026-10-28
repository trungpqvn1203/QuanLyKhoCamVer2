package com.grainwarehouse.product.service;

import com.grainwarehouse.common.exception.DuplicateResourceException;
import com.grainwarehouse.common.exception.ResourceNotFoundException;
import com.grainwarehouse.common.response.PageResponse;
import com.grainwarehouse.product.dto.CreateProductUnitRequest;
import com.grainwarehouse.product.dto.ProductUnitResponse;
import com.grainwarehouse.product.dto.UpdateProductUnitRequest;
import com.grainwarehouse.product.entity.ProductUnit;
import com.grainwarehouse.product.repository.ProductUnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Danh mục đơn vị tính (BR-UNIT-01..04) — cùng cách với danh mục sản phẩm. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductUnitService {

    private final ProductUnitRepository unitRepository;

    public PageResponse<ProductUnitResponse> list(Pageable pageable) {
        return PageResponse.from(unitRepository.findAll(pageable), ProductUnitResponse::from);
    }

    public ProductUnitResponse get(Long id) {
        return ProductUnitResponse.from(findOrThrow(id));
    }

    @Transactional
    public ProductUnitResponse create(CreateProductUnitRequest request) {
        String name = request.name().trim();
        // Kiểm tra trước để trả lỗi rõ ràng; UNIQUE index ở DB là tuyến phòng thủ cuối
        unitRepository.findByNameIgnoreCase(name).ifPresent(this::throwDuplicate);
        ProductUnit unit = new ProductUnit();
        unit.setName(name);
        return ProductUnitResponse.from(unitRepository.save(unit));
    }

    @Transactional
    public ProductUnitResponse update(Long id, UpdateProductUnitRequest request) {
        ProductUnit unit = findOrThrow(id);
        String name = request.name().trim();
        unitRepository.findByNameIgnoreCaseAndIdNot(name, id).ifPresent(this::throwDuplicate);
        unit.setName(name);
        unit.setActive(request.active());
        // saveAndFlush: ép UPDATE chạy ngay để @UpdateTimestamp cập nhật updatedAt trước khi trả response
        return ProductUnitResponse.from(unitRepository.saveAndFlush(unit));
    }

    /** BR-UNIT-02: không xóa cứng (SP cũ vẫn trỏ tới), chỉ ngừng dùng. */
    @Transactional
    public void deactivate(Long id) {
        findOrThrow(id).setActive(false);
    }

    /** BR-UNIT-01: tên trùng (kể cả đơn vị đã ngừng dùng) bị từ chối, kèm hướng dẫn mở lại. */
    private void throwDuplicate(ProductUnit existing) {
        String label = "Đơn vị '" + existing.getName() + "'";
        if (!existing.isActive()) {
            throw new DuplicateResourceException(label
                    + " đã tồn tại nhưng đang ngừng dùng. Hãy mở lại thay vì tạo mới");
        }
        throw new DuplicateResourceException(label + " đã tồn tại");
    }

    private ProductUnit findOrThrow(Long id) {
        return unitRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn vị id=" + id));
    }
}
