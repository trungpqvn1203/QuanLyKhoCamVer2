package com.grainwarehouse.supplier.service;

import com.grainwarehouse.common.exception.BusinessException;
import com.grainwarehouse.common.exception.DuplicateResourceException;
import com.grainwarehouse.common.exception.ResourceNotFoundException;
import com.grainwarehouse.common.response.PageResponse;
import com.grainwarehouse.product.entity.Product;
import com.grainwarehouse.product.repository.ProductRepository;
import com.grainwarehouse.supplier.dto.CreateSupplierRequest;
import com.grainwarehouse.supplier.dto.LinkProductRequest;
import com.grainwarehouse.supplier.dto.SupplierProductResponse;
import com.grainwarehouse.supplier.dto.SupplierResponse;
import com.grainwarehouse.supplier.dto.UpdateSupplierRequest;
import com.grainwarehouse.supplier.entity.ProductSupplier;
import com.grainwarehouse.supplier.entity.ProductSupplierId;
import com.grainwarehouse.supplier.entity.Supplier;
import com.grainwarehouse.supplier.repository.ProductSupplierRepository;
import com.grainwarehouse.supplier.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final ProductSupplierRepository productSupplierRepository;
    private final ProductRepository productRepository;

    public PageResponse<SupplierResponse> list(Pageable pageable) {
        return PageResponse.from(supplierRepository.findAll(pageable), SupplierResponse::from);
    }

    public SupplierResponse get(Long id) {
        return SupplierResponse.from(findOrThrow(id));
    }

    @Transactional
    public SupplierResponse create(CreateSupplierRequest request) {
        String name = request.name().trim();
        supplierRepository.findByNameIgnoreCase(name).ifPresent(this::throwDuplicate);

        Supplier supplier = new Supplier();
        supplier.setName(name);
        supplier.setContactName(request.contactName().trim());
        supplier.setPhone(blankToNull(request.phone()));
        supplier.setEmail(blankToNull(request.email()));
        supplier.setAddress(blankToNull(request.address()));
        // Sau save(), code đã có giá trị nhờ @Generated (BR-SUP-02)
        return SupplierResponse.from(supplierRepository.save(supplier));
    }

    @Transactional
    public SupplierResponse update(Long id, UpdateSupplierRequest request) {
        Supplier supplier = findOrThrow(id);
        String name = request.name().trim();
        supplierRepository.findByNameIgnoreCaseAndIdNot(name, id).ifPresent(this::throwDuplicate);

        supplier.setName(name);
        supplier.setContactName(request.contactName().trim());
        supplier.setPhone(blankToNull(request.phone()));
        supplier.setEmail(blankToNull(request.email()));
        supplier.setAddress(blankToNull(request.address()));
        supplier.setActive(request.active());
        return SupplierResponse.from(supplierRepository.saveAndFlush(supplier));
    }

    /** BR-SUP-04: không xóa cứng. BR-SUP-07: các liên kết NCC–SP được giữ nguyên. */
    @Transactional
    public void deactivate(Long id) {
        findOrThrow(id).setActive(false);
    }

    public PageResponse<SupplierProductResponse> listProducts(Long supplierId, Pageable pageable) {
        findOrThrow(supplierId);
        return PageResponse.from(
                productSupplierRepository.findByIdSupplierIdAndActiveTrue(supplierId, pageable),
                SupplierProductResponse::from);
    }

    /**
     * BR-SUP-05: mỗi cặp (NCC, SP) chỉ có một dòng — đã gỡ thì bật lại dòng cũ thay vì tạo mới.
     * BR-SUP-06: không gán khi NCC ngừng hoạt động hoặc SP ngừng kinh doanh.
     */
    @Transactional
    public SupplierProductResponse linkProduct(Long supplierId, LinkProductRequest request) {
        Supplier supplier = findOrThrow(supplierId);
        if (!supplier.isActive()) {
            throw new BusinessException(label(supplier) + " đang ngừng hoạt động, không thể gán sản phẩm");
        }
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy sản phẩm id=" + request.productId()));
        if (!product.isActive()) {
            throw new BusinessException("Sản phẩm '" + product.getName() + "' (" + product.getCode()
                    + ") đã ngừng kinh doanh, không thể gán cho nhà cung cấp");
        }

        ProductSupplierId id = new ProductSupplierId(supplierId, product.getId());
        ProductSupplier link = productSupplierRepository.findById(id).orElse(null);
        if (link == null) {
            link = new ProductSupplier(supplierId, product);
        } else if (link.isActive()) {
            throw new DuplicateResourceException("Sản phẩm '" + product.getName() + "' (" + product.getCode()
                    + ") đã được gán cho " + label(supplier));
        } else {
            link.setActive(true);
        }
        // Hai request gán cùng cặp đồng thời: request sau vi phạm khóa chính → GlobalExceptionHandler trả 409
        return SupplierProductResponse.from(productSupplierRepository.saveAndFlush(link));
    }

    /** BR-SUP-05: gỡ liên kết = active=false, không xóa dòng. */
    @Transactional
    public void unlinkProduct(Long supplierId, Long productId) {
        findOrThrow(supplierId);
        productSupplierRepository.findById(new ProductSupplierId(supplierId, productId))
                .filter(ProductSupplier::isActive)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Sản phẩm id=" + productId + " chưa được gán cho nhà cung cấp id=" + supplierId))
                .setActive(false);
    }

    /**
     * BR-SUP-03, BR-SUP-04: tên trùng (kể cả với NCC đã ngừng hoạt động) bị từ chối.
     * Nếu bản ghi trùng đang ngừng hoạt động → hướng dẫn mở lại để giữ nguyên mã và lịch sử.
     */
    private void throwDuplicate(Supplier existing) {
        if (!existing.isActive()) {
            throw new DuplicateResourceException(label(existing)
                    + " đã tồn tại nhưng đang ngừng hoạt động. Hãy mở lại nhà cung cấp thay vì tạo mới");
        }
        throw new DuplicateResourceException(label(existing) + " đã tồn tại");
    }

    private static String label(Supplier supplier) {
        return "Nhà cung cấp '" + supplier.getName() + "' (" + supplier.getCode() + ")";
    }

    // Trường tùy chọn: chuỗi rỗng/toàn khoảng trắng lưu thành NULL → bị bỏ khỏi JSON (NON_NULL)
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Supplier findOrThrow(Long id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà cung cấp id=" + id));
    }
}
