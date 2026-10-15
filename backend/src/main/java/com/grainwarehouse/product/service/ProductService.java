package com.grainwarehouse.product.service;

import com.grainwarehouse.common.exception.BusinessException;
import com.grainwarehouse.common.exception.DuplicateResourceException;
import com.grainwarehouse.common.exception.ResourceNotFoundException;
import com.grainwarehouse.common.response.PageResponse;
import com.grainwarehouse.product.dto.CreateProductRequest;
import com.grainwarehouse.product.dto.ProductResponse;
import com.grainwarehouse.product.dto.UpdateProductRequest;
import com.grainwarehouse.product.entity.Category;
import com.grainwarehouse.product.entity.Product;
import com.grainwarehouse.product.entity.ProductUnit;
import com.grainwarehouse.product.repository.CategoryRepository;
import com.grainwarehouse.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public PageResponse<ProductResponse> list(Pageable pageable) {
        return PageResponse.from(productRepository.findAll(pageable), ProductResponse::from);
    }

    public ProductResponse get(Long id) {
        return ProductResponse.from(findOrThrow(id));
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        String name = request.name().trim();
        productRepository.findByNameIgnoreCase(name).ifPresent(this::throwDuplicate);
        validateNetWeight(request.unit(), request.netWeightKg());
        Category category = findActiveCategory(request.categoryId());

        Product product = new Product();
        product.setName(name);
        product.setCategory(category);
        product.setUnit(request.unit());
        product.setNetWeightKg(request.netWeightKg());
        product.setDescription(request.description());
        // Sau save(), code đã có giá trị nhờ @Generated (BR-PRD-02)
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public ProductResponse update(Long id, UpdateProductRequest request) {
        Product product = findOrThrow(id);
        String name = request.name().trim();
        productRepository.findByNameIgnoreCaseAndIdNot(name, id).ifPresent(this::throwDuplicate);
        validateNetWeight(request.unit(), request.netWeightKg());
        // Chỉ chặn khi CHUYỂN sang danh mục khác đang ngừng dùng;
        // sản phẩm cũ vẫn sửa được mô tả dù danh mục hiện tại đã ngừng dùng
        if (!Objects.equals(product.getCategory().getId(), request.categoryId())) {
            product.setCategory(findActiveCategory(request.categoryId()));
        }
        product.setName(name);
        product.setUnit(request.unit());
        product.setNetWeightKg(request.netWeightKg());
        product.setDescription(request.description());
        product.setActive(request.active());
        return ProductResponse.from(productRepository.saveAndFlush(product));
    }

    /** BR-PRD-08: không xóa cứng, chỉ ngừng kinh doanh. */
    @Transactional
    public void deactivate(Long id) {
        findOrThrow(id).setActive(false);
    }

    /**
     * BR-PRD-03, BR-PRD-09: tên trùng (kể cả với sản phẩm đã ngừng kinh doanh) bị từ chối.
     * Nếu bản ghi trùng đang ngừng kinh doanh → hướng dẫn mở bán lại để giữ nguyên mã và lịch sử.
     */
    private void throwDuplicate(Product existing) {
        String label = "Sản phẩm '" + existing.getName() + "' (" + existing.getCode() + ")";
        if (!existing.isActive()) {
            throw new DuplicateResourceException(label
                    + " đã tồn tại nhưng đang ngừng kinh doanh. Hãy mở bán lại thay vì tạo mới");
        }
        throw new DuplicateResourceException(label + " đã tồn tại");
    }

    /** BR-PRD-06: đơn vị KG thì khối lượng một đơn vị phải đúng 1 kg. */
    private void validateNetWeight(ProductUnit unit, BigDecimal netWeightKg) {
        // compareTo thay vì equals: new BigDecimal("1.000").equals(BigDecimal.ONE) == false (khác scale)
        if (unit == ProductUnit.KG && netWeightKg.compareTo(BigDecimal.ONE) != 0) {
            throw new BusinessException("Sản phẩm đơn vị KG phải có khối lượng tịnh bằng 1 kg");
        }
    }

    private Category findActiveCategory(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục id=" + categoryId));
        if (!category.isActive()) {
            throw new BusinessException("Danh mục '" + category.getName() + "' đã ngừng dùng");
        }
        return category;
    }

    private Product findOrThrow(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm id=" + id));
    }
}
