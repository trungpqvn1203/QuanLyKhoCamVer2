package com.grainwarehouse.supplier.service;

import com.grainwarehouse.common.exception.BusinessException;
import com.grainwarehouse.common.exception.DuplicateResourceException;
import com.grainwarehouse.common.exception.InvalidRequestException;
import com.grainwarehouse.common.exception.ResourceNotFoundException;
import com.grainwarehouse.common.response.PageResponse;
import com.grainwarehouse.product.entity.Product;
import com.grainwarehouse.product.repository.ProductRepository;
import com.grainwarehouse.supplier.dto.CreateSupplierRequest;
import com.grainwarehouse.supplier.dto.LinkProductRequest;
import com.grainwarehouse.supplier.dto.ProductSupplierResponse;
import com.grainwarehouse.supplier.dto.SupplierProductResponse;
import com.grainwarehouse.supplier.dto.SupplierResponse;
import com.grainwarehouse.supplier.dto.SupplierSearchCriteria;
import com.grainwarehouse.supplier.dto.UpdateSupplierRequest;
import com.grainwarehouse.supplier.entity.ProductSupplier;
import com.grainwarehouse.supplier.entity.ProductSupplierId;
import com.grainwarehouse.supplier.entity.Supplier;
import com.grainwarehouse.supplier.repository.ProductSupplierRepository;
import com.grainwarehouse.supplier.repository.SupplierRepository;
import com.grainwarehouse.supplier.repository.SupplierSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupplierService {

    private static final Pattern WHITESPACE = Pattern.compile("\\s");

    // Khớp CHECK ck_supplier_tax_code (V15)
    private static final Pattern TAX_CODE = Pattern.compile("\\d{10}(-\\d{3})?");

    private final SupplierRepository supplierRepository;
    private final ProductSupplierRepository productSupplierRepository;
    private final ProductRepository productRepository;

    public PageResponse<SupplierResponse> list(SupplierSearchCriteria criteria, Pageable pageable) {
        return PageResponse.from(
                supplierRepository.findAll(SupplierSpecifications.from(criteria), pageable), SupplierResponse::from);
    }

    public SupplierResponse get(Long id) {
        return SupplierResponse.from(findOrThrow(id));
    }

    @Transactional
    public SupplierResponse create(CreateSupplierRequest request) {
        // Sau save(), code đã có giá trị nhờ @Generated (BR-SUP-02)
        return SupplierResponse.from(supplierRepository.save(prepareNew(request)));
    }

    /**
     * Kiểm tra quy tắc nghiệp vụ và dựng nhà cung cấp CHƯA lưu — dùng chung cho form tạo tay và nhập Excel,
     * để hai đường nhập không bao giờ lệch quy tắc.
     */
    public Supplier prepareNew(CreateSupplierRequest request) {
        String name = request.name().trim();
        supplierRepository.findByNameIgnoreCase(name).ifPresent(this::throwDuplicate);
        String taxCode = normalizeTaxCode(request.taxCode());
        if (taxCode != null) {
            supplierRepository.findByTaxCode(taxCode).ifPresent(this::throwDuplicateTaxCode);
        }

        Supplier supplier = new Supplier();
        supplier.setName(name);
        supplier.setTaxCode(taxCode);
        supplier.setPhone(blankToNull(request.phone()));
        supplier.setEmail(blankToNull(request.email()));
        supplier.setAddress(blankToNull(request.address()));
        return supplier;
    }

    @Transactional
    public SupplierResponse update(Long id, UpdateSupplierRequest request) {
        Supplier supplier = findOrThrow(id);
        String name = request.name().trim();
        supplierRepository.findByNameIgnoreCaseAndIdNot(name, id).ifPresent(this::throwDuplicate);
        String taxCode = normalizeTaxCode(request.taxCode());
        if (taxCode != null) {
            supplierRepository.findByTaxCodeAndIdNot(taxCode, id).ifPresent(this::throwDuplicateTaxCode);
        }

        supplier.setName(name);
        supplier.setTaxCode(taxCode);
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

    /** Chiều ngược của listProducts: NCC đang được phép cung cấp SP này (BR-SUP-05). */
    public PageResponse<ProductSupplierResponse> listSuppliersOfProduct(Long productId, Pageable pageable) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Không tìm thấy sản phẩm id=" + productId);
        }
        return PageResponse.from(
                productSupplierRepository.findByIdProductIdAndActiveTrue(productId, pageable),
                ProductSupplierResponse::from);
    }

    /**
     * BR-SUP-05: mỗi cặp (NCC, SP) chỉ có một dòng — đã gỡ thì bật lại dòng cũ thay vì tạo mới.
     * BR-SUP-06: không gán khi NCC ngừng hoạt động hoặc SP ngừng kinh doanh.
     */
    @Transactional
    public SupplierProductResponse linkProduct(Long supplierId, LinkProductRequest request) {
        Supplier supplier = findOrThrow(supplierId);
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy sản phẩm id=" + request.productId()));
        checkCanLink(supplier, product);

        ProductSupplierId id = new ProductSupplierId(supplierId, product.getId());
        ProductSupplier link = productSupplierRepository.findById(id).orElse(null);
        if (link == null) {
            link = new ProductSupplier(supplier, product);
        } else if (link.isActive()) {
            throw new DuplicateResourceException("Sản phẩm '" + product.getName() + "' (" + product.getCode()
                    + ") đã được gán cho " + label(supplier));
        } else {
            link.setActive(true);
        }
        // Hai request gán cùng cặp đồng thời: request sau vi phạm khóa chính → GlobalExceptionHandler trả 409
        return SupplierProductResponse.from(productSupplierRepository.saveAndFlush(link));
    }

    /**
     * BR-SUP-06 — chỗ kiểm tra DUY NHẤT, dùng cho cả màn NCC (linkProduct) lẫn form / Excel sản phẩm
     * (ProductSupplierLinkService). Chỉ áp cho liên kết tạo mới hoặc bật lại.
     */
    static void checkCanLink(Supplier supplier, Product product) {
        if (!supplier.isActive()) {
            throw new BusinessException(label(supplier) + " đang ngừng hoạt động, không thể gán sản phẩm");
        }
        if (!product.isActive()) {
            // SP đang tạo (chưa lưu) chưa có mã
            String code = product.getCode() == null ? "" : " (" + product.getCode() + ")";
            throw new BusinessException("Sản phẩm '" + product.getName() + "'" + code
                    + " đã ngừng kinh doanh, không thể gán cho nhà cung cấp");
        }
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
            throw new DuplicateResourceException("name", label(existing)
                    + " đã tồn tại nhưng đang ngừng hoạt động. Hãy mở lại nhà cung cấp thay vì tạo mới");
        }
        throw new DuplicateResourceException("name", label(existing) + " đã tồn tại");
    }

    /**
     * BR-SUP-09: MST trùng NCC khác (kể cả đã ngừng) bị từ chối — một MST là một pháp nhân.
     * Hai request cùng MST đồng thời: request sau vi phạm uk_supplier_tax_code → GlobalExceptionHandler trả 409.
     */
    private void throwDuplicateTaxCode(Supplier existing) {
        String message = "Mã số thuế " + existing.getTaxCode() + " đã thuộc về nhà cung cấp '"
                + existing.getName() + "' (" + existing.getCode() + ")";
        if (!existing.isActive()) {
            throw new DuplicateResourceException("taxCode", message
                    + ", nhà cung cấp này đang ngừng hoạt động. Hãy mở lại thay vì tạo mới");
        }
        throw new DuplicateResourceException("taxCode", message);
    }

    /**
     * BR-SUP-09: tùy chọn; bỏ khoảng trắng ("0101 234 567" → "0101234567") rồi kiểm 10 số hoặc 10 số-3 số.
     * Lỗi định dạng → 400 gắn vào field taxCode để form hiện đúng ô.
     */
    private static String normalizeTaxCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String taxCode = WHITESPACE.matcher(raw).replaceAll("");
        if (!TAX_CODE.matcher(taxCode).matches()) {
            throw new InvalidRequestException("taxCode",
                    "Mã số thuế gồm 10 chữ số, hoặc 10 chữ số + '-' + 3 chữ số cho chi nhánh (ví dụ 0101234567-001)");
        }
        return taxCode;
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
