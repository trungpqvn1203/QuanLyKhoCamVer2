package com.grainwarehouse.product.service;

import com.grainwarehouse.common.exception.DuplicateResourceException;
import com.grainwarehouse.common.exception.ResourceNotFoundException;
import com.grainwarehouse.common.response.PageResponse;
import com.grainwarehouse.product.dto.CategoryResponse;
import com.grainwarehouse.product.dto.CreateCategoryRequest;
import com.grainwarehouse.product.dto.UpdateCategoryRequest;
import com.grainwarehouse.product.entity.Category;
import com.grainwarehouse.product.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public PageResponse<CategoryResponse> list(Pageable pageable) {
        return PageResponse.from(categoryRepository.findAll(pageable), CategoryResponse::from);
    }

    public CategoryResponse get(Long id) {
        return CategoryResponse.from(findOrThrow(id));
    }

    @Transactional
    public CategoryResponse create(CreateCategoryRequest request) {
        String name = request.name().trim();
        // BR-CAT-02: kiểm tra trước để trả lỗi rõ ràng; UNIQUE index ở DB là tuyến phòng thủ cuối
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new DuplicateResourceException("Danh mục '" + name + "' đã tồn tại");
        }
        Category category = new Category();
        category.setName(name);
        category.setDescription(request.description());
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(Long id, UpdateCategoryRequest request) {
        Category category = findOrThrow(id);
        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new DuplicateResourceException("Danh mục '" + name + "' đã tồn tại");
        }
        category.setName(name);
        category.setDescription(request.description());
        category.setActive(request.active());
        // saveAndFlush: ép UPDATE chạy ngay để @UpdateTimestamp cập nhật updatedAt trước khi trả response
        return CategoryResponse.from(categoryRepository.saveAndFlush(category));
    }

    /** BR-CAT-03: không xóa cứng, chỉ ngừng dùng. */
    @Transactional
    public void deactivate(Long id) {
        findOrThrow(id).setActive(false);
    }

    private Category findOrThrow(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục id=" + id));
    }
}
