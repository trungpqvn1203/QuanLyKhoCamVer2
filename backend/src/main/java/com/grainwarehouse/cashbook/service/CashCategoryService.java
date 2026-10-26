package com.grainwarehouse.cashbook.service;

import com.grainwarehouse.cashbook.dto.CashCategoryRequest;
import com.grainwarehouse.cashbook.dto.CashCategoryResponse;
import com.grainwarehouse.cashbook.dto.UpdateCashCategoryRequest;
import com.grainwarehouse.cashbook.entity.CashCategory;
import com.grainwarehouse.cashbook.repository.CashCategoryRepository;
import com.grainwarehouse.common.exception.DuplicateResourceException;
import com.grainwarehouse.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Danh mục thu/chi khác (BR-CASH-05). Cùng cách với danh mục sản phẩm: tên không trùng, không xóa cứng. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CashCategoryService {

    private final CashCategoryRepository cashCategoryRepository;

    /** Thu trước, chi sau; trong mỗi chiều theo tên. */
    public List<CashCategoryResponse> list() {
        return cashCategoryRepository.findAll(Sort.by("direction", "name")).stream()
                .map(CashCategoryResponse::from)
                .toList();
    }

    @Transactional
    public CashCategoryResponse create(CashCategoryRequest request) {
        String name = request.name().trim();
        cashCategoryRepository.findByNameIgnoreCase(name).ifPresent(CashCategoryService::throwDuplicate);
        return CashCategoryResponse.from(cashCategoryRepository.save(new CashCategory(name, request.direction())));
    }

    @Transactional
    public CashCategoryResponse update(Long id, UpdateCashCategoryRequest request) {
        CashCategory category = cashCategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục thu/chi id=" + id));
        String name = request.name().trim();
        cashCategoryRepository.findByNameIgnoreCaseAndIdNot(name, id).ifPresent(CashCategoryService::throwDuplicate);
        category.setName(name);
        category.setActive(request.active());
        // saveAndFlush: ép UPDATE chạy ngay để @UpdateTimestamp cập nhật updatedAt trước khi trả response
        return CashCategoryResponse.from(cashCategoryRepository.saveAndFlush(category));
    }

    /** Tên trùng (kể cả danh mục đã ngừng dùng) bị từ chối, kèm hướng dẫn mở lại — như BR-CAT-04. */
    private static void throwDuplicate(CashCategory existing) {
        String label = "Danh mục thu/chi '" + existing.getName() + "'";
        if (!existing.isActive()) {
            throw new DuplicateResourceException("name", label
                    + " đã tồn tại nhưng đang ngừng dùng. Hãy mở lại thay vì tạo mới");
        }
        throw new DuplicateResourceException("name", label + " đã tồn tại");
    }
}
