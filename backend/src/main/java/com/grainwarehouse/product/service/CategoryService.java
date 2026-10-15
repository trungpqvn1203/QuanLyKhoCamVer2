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
        // Kiểm tra trước để trả lỗi rõ ràng; UNIQUE index ở DB là tuyến phòng thủ cuối
        categoryRepository.findByNameIgnoreCase(name).ifPresent(this::throwDuplicate);
        Category category = new Category();
        category.setName(name);
        category.setDescription(request.description());
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(Long id, UpdateCategoryRequest request) {
        Category category = findOrThrow(id);
        String name = request.name().trim();
        categoryRepository.findByNameIgnoreCaseAndIdNot(name, id).ifPresent(this::throwDuplicate);
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

    /** BR-CAT-02, BR-CAT-04: tên trùng (kể cả danh mục đã ngừng dùng) bị từ chối, kèm hướng dẫn mở lại. */
    private void throwDuplicate(Category existing) {
        String label = "Danh mục '" + existing.getName() + "'";
        if (!existing.isActive()) {
            throw new DuplicateResourceException(label
                    + " đã tồn tại nhưng đang ngừng dùng. Hãy mở lại thay vì tạo mới");
        }
        throw new DuplicateResourceException(label + " đã tồn tại");
    }

    private Category findOrThrow(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục id=" + id));
    }
}
