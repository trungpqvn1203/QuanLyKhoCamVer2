package com.grainwarehouse.inbound.service;

import com.grainwarehouse.common.exception.BusinessException;
import com.grainwarehouse.common.exception.InvalidRequestException;
import com.grainwarehouse.common.exception.ResourceNotFoundException;
import com.grainwarehouse.common.response.PageResponse;
import com.grainwarehouse.common.search.CommonSpecifications;
import com.grainwarehouse.inbound.dto.InboundReceiptLineRequest;
import com.grainwarehouse.inbound.dto.InboundReceiptRequest;
import com.grainwarehouse.inbound.dto.InboundReceiptResponse;
import com.grainwarehouse.inbound.dto.InboundReceiptSearchCriteria;
import com.grainwarehouse.inbound.dto.InboundReceiptSummaryResponse;
import com.grainwarehouse.inbound.repository.InboundReceiptSpecifications;
import com.grainwarehouse.inventory.entity.StockDocument;
import com.grainwarehouse.inventory.entity.StockDocumentItem;
import com.grainwarehouse.inventory.entity.StockDocumentStatus;
import com.grainwarehouse.inventory.entity.StockDocumentType;
import com.grainwarehouse.inventory.repository.StockDocumentRepository;
import com.grainwarehouse.inventory.repository.StockTransactionRepository;
import com.grainwarehouse.inventory.service.InventoryService;
import com.grainwarehouse.product.entity.Product;
import com.grainwarehouse.product.repository.ProductRepository;
import com.grainwarehouse.supplier.entity.ProductSupplier;
import com.grainwarehouse.supplier.entity.ProductSupplierId;
import com.grainwarehouse.supplier.entity.Supplier;
import com.grainwarehouse.supplier.repository.ProductSupplierRepository;
import com.grainwarehouse.supplier.repository.SupplierRepository;
import com.grainwarehouse.warehouse.entity.Warehouse;
import com.grainwarehouse.warehouse.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.grainwarehouse.inventory.entity.StockDocumentStatus.COMPLETED;
import static com.grainwarehouse.inventory.entity.StockDocumentStatus.DRAFT;
import static com.grainwarehouse.inventory.entity.StockDocumentStatus.REVERSED;

/**
 * Phiếu nhập kho (BR-IMP-01..14).
 *
 * <p>Bảo vệ trạng thái (BR-IMP-11..14): mọi thao tác làm thay đổi phiếu — sửa, hoàn tất, hủy, đảo —
 * đều bắt đầu bằng {@link #lockForChange}: khóa dòng phiếu (SELECT ... FOR UPDATE) rồi mới kiểm tra
 * trạng thái, bên trong cùng transaction. Khóa chỉ có tác dụng khi MỌI đường thay đổi phiếu đều đi qua đây;
 * thêm thao tác mới mà đọc phiếu bằng findById rồi sửa là mở lại lỗ hổng.
 * Không có thao tác xóa phiếu ở bất kỳ trạng thái nào (BR-IMP-13).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InboundReceiptService {

    private static final StockDocumentType TYPE = StockDocumentType.INBOUND;
    private static final String CODE_FORMAT = "PN%06d";

    private final StockDocumentRepository stockDocumentRepository;
    private final StockTransactionRepository stockTransactionRepository;
    private final WarehouseRepository warehouseRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final ProductSupplierRepository productSupplierRepository;
    private final InventoryService inventoryService;

    public PageResponse<InboundReceiptSummaryResponse> list(InboundReceiptSearchCriteria criteria, Pageable pageable) {
        return PageResponse.from(
                stockDocumentRepository.findAll(InboundReceiptSpecifications.from(criteria), pageable),
                InboundReceiptSummaryResponse::from);
    }

    public InboundReceiptResponse get(Long id) {
        return InboundReceiptResponse.from(stockDocumentRepository.findWithDetailsByIdAndType(id, TYPE)
                .orElseThrow(() -> notFound(id)));
    }

    /** BR-IMP-02: tạo phiếu ở trạng thái Nháp — chưa ảnh hưởng tồn. */
    @Transactional
    public InboundReceiptResponse create(InboundReceiptRequest request) {
        References refs = resolve(request);
        // Sinh mã sau khi kiểm tra xong để request sai không làm nhảy số (nhảy số vẫn chấp nhận, BR-IMP-07)
        String code = CODE_FORMAT.formatted(stockDocumentRepository.nextInboundCodeNumber());
        StockDocument document = new StockDocument(TYPE, code);
        apply(document, request, refs);
        return InboundReceiptResponse.from(stockDocumentRepository.save(document));
    }

    /** BR-IMP-11: chỉ phiếu Nháp được sửa, kể cả dòng hàng. */
    @Transactional
    public InboundReceiptResponse update(Long id, InboundReceiptRequest request) {
        StockDocument document = lockForChange(id, DRAFT, "sửa");
        References refs = resolve(request);
        apply(document, request, refs);
        return InboundReceiptResponse.from(stockDocumentRepository.saveAndFlush(document));
    }

    /**
     * BR-IMP-04: cộng tồn theo SL thực nhận và ghi StockTransaction cho mọi dòng, trong một transaction.
     * Lỗi ở bất kỳ dòng nào → rollback toàn bộ, phiếu vẫn là Nháp.
     */
    @Transactional
    public InboundReceiptResponse complete(Long id) {
        StockDocument document = lockForChange(id, DRAFT, "hoàn tất");
        // BR-IMP-10, BR-IMP-A04: kiểm tra lại — kho/NCC/SP/liên kết có thể đã đổi từ lúc lưu nháp
        checkUsable(document.getWarehouse(), document.getSupplier(),
                document.getItems().stream().map(StockDocumentItem::getProduct).toList());

        // Khóa dòng tồn theo product_id tăng dần — mọi nghiệp vụ cùng một thứ tự → giảm nguy cơ deadlock
        List<StockDocumentItem> received = document.getItems().stream()
                .filter(item -> item.getQuantity() > 0)
                .sorted(Comparator.comparing((StockDocumentItem item) -> item.getProduct().getId()))
                .toList();
        if (received.isEmpty()) {
            throw new BusinessException(label(document)
                    + " không có dòng nào có số lượng thực nhận lớn hơn 0, không thể hoàn tất");
        }
        received.forEach(item -> inventoryService.stockIn(item, document.getWarehouse(), item.getQuantity()));

        document.markCompleted(Instant.now());
        return InboundReceiptResponse.from(stockDocumentRepository.saveAndFlush(document));
    }

    /** BR-IMP-05, BR-IMP-11: chỉ phiếu Nháp được hủy; phiếu vẫn còn để tra cứu. */
    @Transactional
    public InboundReceiptResponse cancel(Long id, String reason) {
        StockDocument document = lockForChange(id, DRAFT, "hủy");
        document.markCancelled(blankToNull(reason), Instant.now());
        return InboundReceiptResponse.from(stockDocumentRepository.saveAndFlush(document));
    }

    /**
     * BR-IMP-06, BR-IMP-12: chỉ phiếu Hoàn tất được đảo, và chỉ một lần. Mỗi giao dịch IN gốc
     * được đảo bằng một giao dịch OUT trỏ về nó. Một dòng không đủ tồn → rollback toàn bộ.
     */
    @Transactional
    public InboundReceiptResponse reverse(Long id, String reason) {
        StockDocument document = lockForChange(id, COMPLETED, "đảo");
        stockTransactionRepository.findOriginalsOfDocument(document.getId()).forEach(inventoryService::reverse);
        document.markReversed(reason.trim(), Instant.now());
        return InboundReceiptResponse.from(stockDocumentRepository.saveAndFlush(document));
    }

    /**
     * Cửa duy nhất để thay đổi phiếu (BR-IMP-14): khóa trước, kiểm tra trạng thái sau.
     * Nếu kiểm tra trước rồi mới khóa (hoặc không khóa), hai người bấm "Hoàn tất" cùng lúc đều thấy Nháp
     * → tồn bị cộng hai lần. Có khóa: người thứ hai chờ, rồi đọc thấy Hoàn tất → bị từ chối.
     * Phải là lần đọc phiếu ĐẦU TIÊN trong transaction: phiếu đã nằm sẵn trong persistence context thì
     * Hibernate trả lại object trong bộ nhớ, trạng thái có thể đã cũ.
     */
    private StockDocument lockForChange(Long id, StockDocumentStatus required, String action) {
        StockDocument document = stockDocumentRepository.findForUpdate(id, TYPE).orElseThrow(() -> notFound(id));
        if (document.getStatus() != required) {
            throw new BusinessException(statusViolation(document, required, action));
        }
        return document;
    }

    private static String statusViolation(StockDocument document, StockDocumentStatus required, String action) {
        if (required == COMPLETED && document.getStatus() == REVERSED) {
            return label(document) + " đã được đảo. Mỗi phiếu chỉ được đảo một lần";
        }
        return label(document) + " đang ở trạng thái " + document.getStatus().label()
                + ". Chỉ phiếu " + required.label() + " mới được " + action;
    }

    /** Đọc và kiểm tra mọi thứ request tham chiếu tới, TRƯỚC khi đổi phiếu. Thứ tự: 400 → 404 → 409. */
    private References resolve(InboundReceiptRequest request) {
        LocalDate documentDate = resolveDocumentDate(request.documentDate());
        List<Long> productIds = distinctProductIds(request.lines());

        Warehouse warehouse = warehouseRepository.findById(request.warehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kho id=" + request.warehouseId()));
        Supplier supplier = supplierRepository.findById(request.supplierId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy nhà cung cấp id=" + request.supplierId()));
        Map<Long, Product> products = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        productIds.stream().filter(productId -> !products.containsKey(productId)).findFirst().ifPresent(productId -> {
            throw new ResourceNotFoundException("Không tìm thấy sản phẩm id=" + productId);
        });

        checkUsable(warehouse, supplier, products.values());
        return new References(warehouse, supplier, products, documentDate);
    }

    // BR-IMP-08: mặc định hôm nay, lùi ngày được, không được ở tương lai — "hôm nay" theo giờ Việt Nam,
    // không theo múi giờ của server (server UTC lúc 0h30 sáng VN vẫn đang là "hôm qua")
    private static LocalDate resolveDocumentDate(LocalDate requested) {
        LocalDate today = LocalDate.now(CommonSpecifications.BUSINESS_ZONE);
        if (requested == null) {
            return today;
        }
        if (requested.isAfter(today)) {
            throw new InvalidRequestException("documentDate", "Ngày chứng từ không được ở tương lai");
        }
        return requested;
    }

    // BR-IMP-A05: mỗi SP một dòng
    private static List<Long> distinctProductIds(List<InboundReceiptLineRequest> lines) {
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < lines.size(); i++) {
            if (!seen.add(lines.get(i).productId())) {
                throw new InvalidRequestException("lines[" + i + "].productId",
                        "Sản phẩm này đã có ở một dòng khác của phiếu");
            }
        }
        return lines.stream().map(InboundReceiptLineRequest::productId).toList();
    }

    /** BR-IMP-A04 (kho, NCC, SP đang hoạt động) và BR-SUP-08 / BR-IMP-10 (SP đang liên kết với NCC). */
    private void checkUsable(Warehouse warehouse, Supplier supplier, Collection<Product> products) {
        if (!warehouse.isActive()) {
            throw new BusinessException("Kho '" + warehouse.getName() + "' (" + warehouse.getCode()
                    + ") đang ngừng hoạt động, không thể nhập kho");
        }
        if (!supplier.isActive()) {
            throw new BusinessException(supplierLabel(supplier) + " đang ngừng hoạt động, không thể nhập hàng");
        }
        List<Product> inactive = products.stream().filter(product -> !product.isActive()).toList();
        if (!inactive.isEmpty()) {
            throw new BusinessException("Sản phẩm đã ngừng kinh doanh, không thể nhập: " + productLabels(inactive));
        }

        Set<ProductSupplierId> linkIds = products.stream()
                .map(product -> new ProductSupplierId(supplier.getId(), product.getId()))
                .collect(Collectors.toSet());
        Set<Long> linkedProductIds = productSupplierRepository.findAllById(linkIds).stream()
                .filter(ProductSupplier::isActive)
                .map(link -> link.getId().getProductId())
                .collect(Collectors.toSet());
        List<Product> unlinked = products.stream()
                .filter(product -> !linkedProductIds.contains(product.getId()))
                .toList();
        if (!unlinked.isEmpty()) {
            throw new BusinessException("Sản phẩm chưa được gán cho " + supplierLabel(supplier) + ": "
                    + productLabels(unlinked) + ". Hãy gán sản phẩm cho nhà cung cấp trước khi nhập");
        }
    }

    /**
     * Ghi header và dòng hàng từ request. Dòng được cập nhật TẠI CHỖ theo productId (không xóa hết rồi
     * thêm lại): dòng giữ nguyên id/created_at, ít câu SQL hơn, và không phụ thuộc vào thứ tự Hibernate
     * xếp DELETE/INSERT khi flush — "xóa SP A rồi thêm lại SP A" mà INSERT chạy trước sẽ vi phạm
     * UNIQUE (document_id, product_id).
     */
    private static void apply(StockDocument document, InboundReceiptRequest request, References refs) {
        document.setWarehouse(refs.warehouse());
        document.setSupplier(refs.supplier());
        document.setDocumentDate(refs.documentDate());
        document.setNote(blankToNull(request.note()));

        Set<Long> requestedIds = refs.products().keySet();
        document.removeItemsIf(item -> !requestedIds.contains(item.getProduct().getId()));
        Map<Long, StockDocumentItem> existing = document.getItems().stream()
                .collect(Collectors.toMap(item -> item.getProduct().getId(), Function.identity()));

        for (InboundReceiptLineRequest line : request.lines()) {
            StockDocumentItem item = existing.get(line.productId());
            if (item == null) {
                item = new StockDocumentItem(refs.products().get(line.productId()));
                document.addItem(item);
            }
            item.setValues(line.orderedQuantity(), line.effectiveReceivedQuantity(), line.unitPrice());
        }
        document.recalculateTotal();
    }

    private static String label(StockDocument document) {
        return "Phiếu nhập " + document.getCode();
    }

    private static String supplierLabel(Supplier supplier) {
        return "nhà cung cấp '" + supplier.getName() + "' (" + supplier.getCode() + ")";
    }

    private static String productLabels(List<Product> products) {
        return products.stream()
                .map(product -> "'" + product.getName() + "' (" + product.getCode() + ")")
                .collect(Collectors.joining(", "));
    }

    private static ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException("Không tìm thấy phiếu nhập id=" + id);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record References(Warehouse warehouse, Supplier supplier, Map<Long, Product> products,
                              LocalDate documentDate) {
    }
}
