package com.grainwarehouse.opening.service;

import com.grainwarehouse.common.exception.BusinessException;
import com.grainwarehouse.common.exception.InvalidRequestException;
import com.grainwarehouse.common.exception.ResourceNotFoundException;
import com.grainwarehouse.common.response.PageResponse;
import com.grainwarehouse.common.search.CommonSpecifications;
import com.grainwarehouse.inventory.entity.StockDocument;
import com.grainwarehouse.inventory.entity.StockDocumentItem;
import com.grainwarehouse.inventory.entity.StockDocumentStatus;
import com.grainwarehouse.inventory.entity.StockDocumentType;
import com.grainwarehouse.inventory.entity.StockTransaction;
import com.grainwarehouse.inventory.repository.DocumentLineTotals;
import com.grainwarehouse.inventory.repository.StockDocumentItemRepository;
import com.grainwarehouse.inventory.repository.StockDocumentRepository;
import com.grainwarehouse.inventory.repository.StockTransactionRepository;
import com.grainwarehouse.inventory.service.InventoryService;
import com.grainwarehouse.opening.dto.OpeningStockLineRequest;
import com.grainwarehouse.opening.dto.OpeningStockRequest;
import com.grainwarehouse.opening.dto.OpeningStockResponse;
import com.grainwarehouse.opening.dto.OpeningStockSearchCriteria;
import com.grainwarehouse.opening.dto.OpeningStockSummaryResponse;
import com.grainwarehouse.opening.repository.OpeningStockSpecifications;
import com.grainwarehouse.product.entity.Product;
import com.grainwarehouse.product.repository.ProductRepository;
import com.grainwarehouse.warehouse.entity.Warehouse;
import com.grainwarehouse.warehouse.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
 * Phiếu tồn đầu kỳ (BR-OPN-01..10): khai báo hàng đang có sẵn trong kho khi bắt đầu quản lý.
 *
 * <p>Cùng vòng đời với phiếu nhập — Nháp → Hoàn tất → Đã đảo, Nháp → Đã hủy — và cùng cách bảo vệ trạng thái:
 * mọi thao tác thay đổi phiếu bắt đầu bằng {@link #lockForChange} (khóa FOR UPDATE rồi mới kiểm tra, BR-IMP-14).
 * Tồn và lịch sử đi qua {@link InventoryService} như mọi chứng từ khác — không có đường ghi tồn riêng.
 *
 * <p>Điểm khác phiếu nhập (BR-OPN-05): mỗi cặp (SP, kho) chỉ được khởi tạo khi CHƯA có biến động tồn nào.
 * Đã nhập/bán rồi mà lệch số thì là việc của kiểm kê, không phải đầu kỳ.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OpeningStockService {

    private static final StockDocumentType TYPE = StockDocumentType.OPENING_STOCK;
    private static final String CODE_FORMAT = "DTK%06d";

    private final StockDocumentRepository stockDocumentRepository;
    private final StockDocumentItemRepository stockDocumentItemRepository;
    private final StockTransactionRepository stockTransactionRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;

    public PageResponse<OpeningStockSummaryResponse> list(OpeningStockSearchCriteria criteria, Pageable pageable) {
        Page<StockDocument> page = stockDocumentRepository.findAll(OpeningStockSpecifications.from(criteria), pageable);
        // Số dòng, tổng SL: một câu GROUP BY cho cả trang thay vì tải dòng hàng của từng phiếu
        List<Long> ids = page.getContent().stream().map(StockDocument::getId).toList();
        Map<Long, DocumentLineTotals> totals = ids.isEmpty() ? Map.of()
                : stockDocumentItemRepository.sumByDocument(ids).stream()
                        .collect(Collectors.toMap(DocumentLineTotals::getDocumentId, Function.identity()));
        return PageResponse.from(page, document -> OpeningStockSummaryResponse.from(document, totals.get(document.getId())));
    }

    public OpeningStockResponse get(Long id) {
        return OpeningStockResponse.from(stockDocumentRepository.findWithDetailsByIdAndType(id, TYPE)
                .orElseThrow(() -> notFound(id)));
    }

    /** SP đã có biến động tồn tại kho — không nhập đầu kỳ được nữa (BR-OPN-05). Giao diện dùng để làm mờ. */
    public List<Long> productsWithMovement(Long warehouseId) {
        if (!warehouseRepository.existsById(warehouseId)) {
            throw new ResourceNotFoundException("Không tìm thấy kho id=" + warehouseId);
        }
        return stockTransactionRepository.findProductIdsWithMovement(warehouseId);
    }

    /** BR-OPN-03: tạo Nháp — chưa ảnh hưởng tồn. SP đã có biến động vẫn lưu nháp được, chặn lúc xác nhận. */
    @Transactional
    public OpeningStockResponse create(OpeningStockRequest request, String username) {
        References refs = resolve(request);
        String code = CODE_FORMAT.formatted(stockDocumentRepository.nextOpeningStockCodeNumber());
        StockDocument document = new StockDocument(TYPE, code, username);
        apply(document, request, refs);
        return OpeningStockResponse.from(stockDocumentRepository.save(document));
    }

    @Transactional
    public OpeningStockResponse update(Long id, OpeningStockRequest request) {
        StockDocument document = lockForChange(id, DRAFT, "sửa");
        References refs = resolve(request);
        apply(document, request, refs);
        return OpeningStockResponse.from(stockDocumentRepository.saveAndFlush(document));
    }

    /**
     * Xác nhận: cộng tồn và ghi StockTransaction cho mọi dòng trong MỘT transaction; một dòng lỗi → rollback hết,
     * phiếu vẫn là Nháp.
     *
     * <p>BR-OPN-05 có hai lớp:
     * <ol>
     *   <li>Kiểm tra trước: cặp (SP, kho) chưa có biến động nào — báo lỗi rõ ràng, liệt kê mọi SP vi phạm.</li>
     *   <li>Chốt chặn sau khi cộng: tồn TRƯỚC khi cộng phải bằng 0. Bước 1 đọc lúc chưa khóa nên còn khe hở: hai
     *   phiếu đầu kỳ cùng chứa một SP (hoặc một phiếu nhập) xác nhận cùng lúc đều thấy "chưa có biến động". Câu cộng
     *   tồn khóa dòng inventory → người đến sau chờ, rồi thấy tồn khác 0 → rollback.</li>
     * </ol>
     */
    @Transactional
    public OpeningStockResponse complete(Long id, String username) {
        StockDocument document = lockForChange(id, DRAFT, "xác nhận");
        Warehouse warehouse = document.getWarehouse();
        List<Product> products = document.getItems().stream().map(StockDocumentItem::getProduct).toList();
        checkUsable(warehouse, products);
        checkNoMovement(warehouse, products);

        // Khóa dòng tồn theo product_id tăng dần — cùng thứ tự với mọi nghiệp vụ khác, giảm nguy cơ deadlock
        document.getItems().stream()
                .sorted(Comparator.comparing((StockDocumentItem item) -> item.getProduct().getId()))
                .forEach(item -> {
                    StockTransaction transaction = inventoryService.stockIn(item, warehouse, item.getQuantity());
                    if (transaction.getQuantityBefore() != 0) {
                        throw new BusinessException("Sản phẩm " + productLabel(item.getProduct())
                                + " vừa phát sinh tồn tại kho này. Tồn đầu kỳ chỉ dùng cho sản phẩm chưa có biến động");
                    }
                });

        document.markCompleted(Instant.now(), username);
        return OpeningStockResponse.from(stockDocumentRepository.saveAndFlush(document));
    }

    @Transactional
    public OpeningStockResponse cancel(Long id, String reason) {
        StockDocument document = lockForChange(id, DRAFT, "hủy");
        document.markCancelled(blankToNull(reason), Instant.now());
        return OpeningStockResponse.from(stockDocumentRepository.saveAndFlush(document));
    }

    /**
     * BR-OPN-06: đảo toàn phiếu, mỗi phiếu một lần. Hàng đã bán bớt, không đủ để trừ lại → từ chối (BR-INV-01).
     * Đảo xong thì các SP của phiếu được nhập đầu kỳ lại (nếu ngoài phiếu này chưa có biến động nào).
     */
    @Transactional
    public OpeningStockResponse reverse(Long id, String reason) {
        StockDocument document = lockForChange(id, COMPLETED, "đảo");
        stockTransactionRepository.findOriginalsOfDocument(document.getId()).forEach(inventoryService::reverse);
        document.markReversed(reason.trim(), Instant.now());
        return OpeningStockResponse.from(stockDocumentRepository.saveAndFlush(document));
    }

    /** Khóa trước, kiểm tra trạng thái sau (BR-IMP-14) — xem InboundReceiptService.lockForChange. */
    private StockDocument lockForChange(Long id, StockDocumentStatus required, String action) {
        StockDocument document = stockDocumentRepository.findForUpdate(id, TYPE).orElseThrow(() -> notFound(id));
        if (document.getStatus() != required) {
            if (required == COMPLETED && document.getStatus() == REVERSED) {
                throw new BusinessException(label(document) + " đã được đảo. Mỗi phiếu chỉ được đảo một lần");
            }
            throw new BusinessException(label(document) + " đang ở trạng thái " + document.getStatus().label()
                    + ". Chỉ phiếu " + required.label() + " mới được " + action);
        }
        return document;
    }

    /** Thứ tự lỗi: 400 → 404 → 409. */
    private References resolve(OpeningStockRequest request) {
        LocalDate documentDate = resolveDocumentDate(request.documentDate());
        List<Long> productIds = distinctProductIds(request.lines());

        Warehouse warehouse = warehouseRepository.findById(request.warehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kho id=" + request.warehouseId()));
        Map<Long, Product> products = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        productIds.stream().filter(productId -> !products.containsKey(productId)).findFirst().ifPresent(productId -> {
            throw new ResourceNotFoundException("Không tìm thấy sản phẩm id=" + productId);
        });

        checkUsable(warehouse, products.values());
        return new References(warehouse, products, documentDate);
    }

    // BR-OPN-01: mặc định hôm nay theo giờ Việt Nam, lùi được, không ở tương lai
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

    // BR-OPN-02: mỗi SP một dòng
    private static List<Long> distinctProductIds(List<OpeningStockLineRequest> lines) {
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < lines.size(); i++) {
            if (!seen.add(lines.get(i).productId())) {
                throw new InvalidRequestException("lines[" + i + "].productId",
                        "Sản phẩm này đã có ở một dòng khác của phiếu");
            }
        }
        return lines.stream().map(OpeningStockLineRequest::productId).toList();
    }

    /** BR-OPN-04: kho và SP đang hoạt động — kiểm tra cả lúc lưu nháp lẫn lúc xác nhận. */
    private static void checkUsable(Warehouse warehouse, Collection<Product> products) {
        if (!warehouse.isActive()) {
            throw new BusinessException("Kho '" + warehouse.getName() + "' (" + warehouse.getCode()
                    + ") đang ngừng hoạt động, không thể nhập tồn đầu kỳ");
        }
        List<Product> inactive = products.stream().filter(product -> !product.isActive()).toList();
        if (!inactive.isEmpty()) {
            throw new BusinessException("Sản phẩm đã ngừng kinh doanh, không thể nhập tồn đầu kỳ: "
                    + productLabels(inactive));
        }
    }

    /** BR-OPN-05 lớp 1: liệt kê MỌI SP vi phạm trong một lần báo lỗi để người dùng sửa một lượt. */
    private void checkNoMovement(Warehouse warehouse, List<Product> products) {
        Set<Long> moved = new HashSet<>(stockTransactionRepository.findProductIdsWithMovement(
                warehouse.getId(), products.stream().map(Product::getId).toList()));
        List<Product> blocked = products.stream().filter(product -> moved.contains(product.getId())).toList();
        if (!blocked.isEmpty()) {
            throw new BusinessException("Sản phẩm đã có nhập/xuất tại kho '" + warehouse.getName() + "', không nhập tồn"
                    + " đầu kỳ được nữa: " + productLabels(blocked) + ". Hãy bỏ các dòng này; lệch số thì dùng kiểm kê");
        }
    }

    /** Dòng được cập nhật tại chỗ theo productId — xem InboundReceiptService.apply. */
    private static void apply(StockDocument document, OpeningStockRequest request, References refs) {
        document.setWarehouse(refs.warehouse());
        document.setDocumentDate(refs.documentDate());
        document.setNote(blankToNull(request.note()));

        Set<Long> requestedIds = refs.products().keySet();
        document.removeItemsIf(item -> !requestedIds.contains(item.getProduct().getId()));
        Map<Long, StockDocumentItem> existing = document.getItems().stream()
                .collect(Collectors.toMap(item -> item.getProduct().getId(), Function.identity()));

        for (OpeningStockLineRequest line : request.lines()) {
            StockDocumentItem item = existing.get(line.productId());
            if (item == null) {
                item = new StockDocumentItem(refs.products().get(line.productId()));
                document.addItem(item);
            }
            item.setValues(null, line.quantity(), line.effectiveUnitPrice());
        }
        document.recalculateTotal();
    }

    private static String label(StockDocument document) {
        return "Phiếu tồn đầu kỳ " + document.getCode();
    }

    private static String productLabel(Product product) {
        return "'" + product.getName() + "' (" + product.getCode() + ")";
    }

    private static String productLabels(List<Product> products) {
        return products.stream().map(OpeningStockService::productLabel).collect(Collectors.joining(", "));
    }

    private static ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException("Không tìm thấy phiếu tồn đầu kỳ id=" + id);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record References(Warehouse warehouse, Map<Long, Product> products, LocalDate documentDate) {
    }
}
