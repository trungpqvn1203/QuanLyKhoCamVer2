package com.grainwarehouse.product.service;

import com.grainwarehouse.common.exception.BusinessException;
import com.grainwarehouse.common.exception.DuplicateResourceException;
import com.grainwarehouse.common.exception.ResourceNotFoundException;
import com.grainwarehouse.common.response.PageResponse;
import com.grainwarehouse.product.dto.CreateProductRequest;
import com.grainwarehouse.product.dto.ProductResponse;
import com.grainwarehouse.product.dto.ProductSearchCriteria;
import com.grainwarehouse.product.dto.UpdateProductRequest;
import com.grainwarehouse.product.entity.Category;
import com.grainwarehouse.product.entity.Product;
import com.grainwarehouse.product.entity.ProductUnit;
import com.grainwarehouse.product.repository.CategoryRepository;
import com.grainwarehouse.product.repository.ProductRepository;
import com.grainwarehouse.product.repository.ProductSpecifications;
import com.grainwarehouse.product.repository.ProductUnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductUnitRepository unitRepository;
    private final ProductSupplierLinks supplierLinks;

    /** Tham số lọc đều tùy chọn; null = không lọc theo tiêu chí đó. */
    public PageResponse<ProductResponse> list(ProductSearchCriteria criteria, Pageable pageable) {
        return PageResponse.from(
                productRepository.findAll(ProductSpecifications.from(criteria), pageable), ProductResponse::from);
    }

    public ProductResponse get(Long id) {
        return ProductResponse.from(findOrThrow(id));
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        // Sau save(), code đã có giá trị nhờ @Generated (BR-PRD-02)
        Product product = productRepository.save(prepareNew(request));
        // BR-PRD-10: gán NCC trong cùng transaction — lỗi liên kết thì SP cũng không được tạo
        supplierLinks.sync(product, supplierIds(request.supplierIds()));
        return ProductResponse.from(product);
    }

    /**
     * Kiểm tra quy tắc nghiệp vụ và dựng sản phẩm CHƯA lưu — dùng chung cho form tạo tay và nhập Excel,
     * để hai đường nhập không bao giờ lệch quy tắc.
     */
    public Product prepareNew(CreateProductRequest request) {
        String name = request.name().trim();
        productRepository.findByNameIgnoreCase(name).ifPresent(this::throwDuplicate);
        Category category = findActiveCategory(request.categoryId());
        ProductUnit unit = findActiveUnit(request.unitId());
        validateNetWeight(unit, request.netWeightKg());

        Product product = new Product();
        product.setName(name);
        product.setCategory(category);
        product.setUnit(unit);
        product.setNetWeightKg(request.netWeightKg());
        product.setDescription(request.description());
        product.setMinStockQuantity(request.minStockQuantity());
        supplierLinks.validate(product, supplierIds(request.supplierIds()));
        return product;
    }

    @Transactional
    public ProductResponse update(Long id, UpdateProductRequest request) {
        Product product = findOrThrow(id);
        String name = request.name().trim();
        productRepository.findByNameIgnoreCaseAndIdNot(name, id).ifPresent(this::throwDuplicate);
        // Chỉ chặn khi CHUYỂN sang danh mục / đơn vị khác đang ngừng dùng;
        // sản phẩm cũ vẫn sửa được mô tả dù danh mục / đơn vị hiện tại đã ngừng dùng
        if (!Objects.equals(product.getCategory().getId(), request.categoryId())) {
            product.setCategory(findActiveCategory(request.categoryId()));
        }
        if (!Objects.equals(product.getUnit().getId(), request.unitId())) {
            product.setUnit(findActiveUnit(request.unitId()));
        }
        validateNetWeight(product.getUnit(), request.netWeightKg());
        product.setName(name);
        product.setNetWeightKg(request.netWeightKg());
        product.setDescription(request.description());
        product.setMinStockQuantity(request.minStockQuantity());
        product.setActive(request.active());
        // null = client không gửi (ví dụ nút "Mở bán lại") → giữ nguyên liên kết NCC
        if (request.supplierIds() != null) {
            supplierLinks.sync(product, supplierIds(request.supplierIds()));
        }
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
            throw new DuplicateResourceException("name", label
                    + " đã tồn tại nhưng đang ngừng kinh doanh. Hãy mở bán lại thay vì tạo mới");
        }
        throw new DuplicateResourceException("name", label + " đã tồn tại");
    }

    /** BR-PRD-06, BR-UNIT-03: đơn vị có khối lượng cố định (Kg = 1) thì khối lượng tịnh phải đúng bằng nó. */
    private void validateNetWeight(ProductUnit unit, BigDecimal netWeightKg) {
        BigDecimal fixed = unit.getFixedNetWeightKg();
        // compareTo thay vì equals: new BigDecimal("1.000").equals(BigDecimal.ONE) == false (khác scale)
        if (fixed != null && netWeightKg.compareTo(fixed) != 0) {
            throw new BusinessException("Sản phẩm đơn vị " + unit.getName() + " phải có khối lượng tịnh bằng "
                    + fixed.stripTrailingZeros().toPlainString() + " kg");
        }
    }

    /** Gửi trùng id thì bỏ trùng, giữ thứ tự. */
    private static Set<Long> supplierIds(List<Long> ids) {
        return ids == null ? Set.of() : new LinkedHashSet<>(ids);
    }

    private ProductUnit findActiveUnit(Long unitId) {
        ProductUnit unit = unitRepository.findById(unitId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn vị id=" + unitId));
        if (!unit.isActive()) {
            throw new BusinessException("Đơn vị '" + unit.getName() + "' đã ngừng dùng");
        }
        return unit;
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
