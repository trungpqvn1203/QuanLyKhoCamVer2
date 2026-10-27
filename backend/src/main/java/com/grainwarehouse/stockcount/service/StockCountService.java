package com.grainwarehouse.stockcount.service;

import com.grainwarehouse.common.exception.BusinessException;
import com.grainwarehouse.common.exception.InvalidRequestException;
import com.grainwarehouse.common.exception.ResourceNotFoundException;
import com.grainwarehouse.common.response.PageResponse;
import com.grainwarehouse.common.search.CommonSpecifications;
import com.grainwarehouse.inventory.entity.Inventory;
import com.grainwarehouse.inventory.entity.StockDocument;
import com.grainwarehouse.inventory.entity.StockDocumentItem;
import com.grainwarehouse.inventory.entity.StockDocumentStatus;
import com.grainwarehouse.inventory.entity.StockDocumentType;
import com.grainwarehouse.inventory.repository.InventoryRepository;
import com.grainwarehouse.inventory.repository.QuantityByKey;
import com.grainwarehouse.inventory.repository.StockCountTotals;
import com.grainwarehouse.inventory.repository.StockDocumentItemRepository;
import com.grainwarehouse.inventory.repository.StockDocumentRepository;
import com.grainwarehouse.inventory.repository.StockTransactionRepository;
import com.grainwarehouse.inventory.service.InventoryService;
import com.grainwarehouse.product.entity.Product;
import com.grainwarehouse.product.repository.ProductRepository;
import com.grainwarehouse.stockcount.dto.StockCountCreateRequest;
import com.grainwarehouse.stockcount.dto.StockCountLineRequest;
import com.grainwarehouse.stockcount.dto.StockCountResponse;
import com.grainwarehouse.stockcount.dto.StockCountSearchCriteria;
import com.grainwarehouse.stockcount.dto.StockCountSummaryResponse;
import com.grainwarehouse.stockcount.dto.StockCountUpdateRequest;
import com.grainwarehouse.stockcount.entity.StockCountReason;
import com.grainwarehouse.stockcount.repository.StockCountReasonRepository;
import com.grainwarehouse.stockcount.repository.StockCountSpecifications;
import com.grainwarehouse.warehouse.entity.Warehouse;
import com.grainwarehouse.warehouse.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
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
 * Kiểm kê kho (BR-CNT-01..10): so SL đếm thực tế với SL sổ sách, ghi PHẦN CHÊNH kèm lý do.
 *
 * <p>Vì sao ghi phần chênh mà không "đặt tồn = số đếm": kho vẫn nhập/bán trong lúc đếm (không khóa kho, BR-CNT-03).
 * Sổ sách 120, đếm 115 lúc 8h, 9h bán 10 → tồn 110. Đặt tồn = 115 lúc xác nhận thì 10 bao vừa bán "sống lại";
 * ghi chênh −5 thì tồn = 105, đúng. Đổi lại phải đếm ngay sau khi chụp sổ sách — có cảnh báo SP phát sinh sau mốc chụp.
 *
 * <p>Vòng đời và bảo vệ trạng thái như phiếu nhập: khóa FOR UPDATE rồi mới kiểm tra (BR-IMP-14); tồn và lịch sử
 * qua {@link InventoryService} (thừa → stockIn, thiếu → stockOut, đảo → reverse).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockCountService {

    private static final StockDocumentType TYPE = StockDocumentType.STOCK_COUNT;
    private static final String CODE_FORMAT = "KK%06d";

    private final StockDocumentRepository stockDocumentRepository;
    private final StockDocumentItemRepository stockDocumentItemRepository;
    private final StockTransactionRepository stockTransactionRepository;
    private final InventoryRepository inventoryRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final StockCountReasonRepository stockCountReasonRepository;
    private final InventoryService inventoryService;

    public PageResponse<StockCountSummaryResponse> list(StockCountSearchCriteria criteria, Pageable pageable) {
        Page<StockDocument> page = stockDocumentRepository.findAll(StockCountSpecifications.from(criteria), pageable);
        List<Long> ids = page.getContent().stream().map(StockDocument::getId).toList();
        Map<Long, StockCountTotals> totals = ids.isEmpty() ? Map.of()
                : stockDocumentItemRepository.sumCountByDocument(ids).stream()
                        .collect(Collectors.toMap(StockCountTotals::getDocumentId, Function.identity()));
        return PageResponse.from(page, document -> StockCountSummaryResponse.from(document, totals.get(document.getId())));
    }

    public StockCountResponse get(Long id) {
        return toResponse(stockDocumentRepository.findWithDetailsByIdAndType(id, TYPE).orElseThrow(() -> notFound(id)));
    }

    /**
     * BR-CNT-02, 03, 07: lập phiếu cho một kho đang hoạt động, nạp sẵn mọi SP đang có tồn và chụp SL sổ sách ngay lúc
     * này. Kho đang có phiếu kiểm dở → 409 (UNIQUE uk_stock_document_open_count chặn cả khi hai người lập cùng lúc).
     */
    @Transactional
    public StockCountResponse create(StockCountCreateRequest request, String username) {
        LocalDate documentDate = resolveDocumentDate(request.documentDate());
        Warehouse warehouse = warehouseRepository.findById(request.warehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kho id=" + request.warehouseId()));
        checkWarehouseActive(warehouse);
        stockDocumentRepository.findOpenCountCode(warehouse.getId()).ifPresent(code -> {
            throw new BusinessException("Kho '" + warehouse.getName() + "' đang có phiếu kiểm " + code
                    + " chưa xong. Hãy xác nhận hoặc hủy phiếu đó trước");
        });

        StockDocument document = new StockDocument(TYPE, CODE_FORMAT.formatted(
                stockDocumentRepository.nextStockCountCodeNumber()), username);
        document.setWarehouse(warehouse);
        document.setDocumentDate(documentDate);
        document.setNote(blankToNull(request.note()));
        document.setBookSnapshotAt(Instant.now());
        for (Inventory row : inventoryRepository.findInStockByWarehouse(warehouse.getId())) {
            StockDocumentItem item = new StockDocumentItem(row.getProduct());
            item.setCount(row.getQuantity(), null, null, null);
            document.addItem(item);
        }
        // Flush ngay: vi phạm UNIQUE (hai người lập cùng lúc) nổ ra ở đây thành 409
        return toResponse(stockDocumentRepository.saveAndFlush(document));
    }

    /**
     * Lưu nháp: thay toàn bộ dòng. Dòng đã có giữ nguyên SL sổ sách đã chụp; SP mới thêm chụp SL sổ sách lúc thêm.
     * Lý do (nếu có) phải tồn tại; chưa bắt buộc lúc nháp — bắt buộc lúc xác nhận (BR-CNT-05).
     */
    @Transactional
    public StockCountResponse update(Long id, StockCountUpdateRequest request) {
        StockDocument document = lockForChange(id, DRAFT, "sửa");
        LocalDate documentDate = resolveDocumentDate(request.documentDate());
        List<Long> productIds = distinctProductIds(request.lines());
        Map<Long, Product> products = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        productIds.stream().filter(productId -> !products.containsKey(productId)).findFirst().ifPresent(productId -> {
            throw new ResourceNotFoundException("Không tìm thấy sản phẩm id=" + productId);
        });
        Map<Long, StockCountReason> reasons = loadReasons(request.lines());

        document.setDocumentDate(documentDate);
        document.setNote(blankToNull(request.note()));
        Set<Long> requested = new HashSet<>(productIds);
        document.removeItemsIf(item -> !requested.contains(item.getProduct().getId()));
        Map<Long, StockDocumentItem> existing = document.getItems().stream()
                .collect(Collectors.toMap(item -> item.getProduct().getId(), Function.identity()));
        Map<Long, Integer> currentStock = currentStock(document.getWarehouse(),
                productIds.stream().filter(productId -> !existing.containsKey(productId)).toList());

        for (StockCountLineRequest line : request.lines()) {
            StockDocumentItem item = existing.get(line.productId());
            int book;
            if (item == null) {
                item = new StockDocumentItem(products.get(line.productId()));
                document.addItem(item);
                book = currentStock.getOrDefault(line.productId(), 0);
            } else {
                book = item.getBookQuantity();
            }
            item.setCount(book, line.countedQuantity(),
                    line.reasonId() == null ? null : reasons.get(line.reasonId()), blankToNull(line.reasonNote()));
        }
        return toResponse(stockDocumentRepository.saveAndFlush(document));
    }

    /**
     * BR-CNT-03: chụp lại SL sổ sách của MỌI dòng theo tồn hiện tại và dời mốc chụp — dùng khi bắt đầu đếm muộn hơn
     * lúc lập phiếu, hoặc sau cảnh báo có phát sinh. SL đếm đã nhập giữ nguyên (chênh lệch tính lại).
     */
    @Transactional
    public StockCountResponse refreshBook(Long id) {
        StockDocument document = lockForChange(id, DRAFT, "lấy lại số sổ sách");
        Map<Long, Integer> stock = currentStock(document.getWarehouse(),
                document.getItems().stream().map(item -> item.getProduct().getId()).toList());
        for (StockDocumentItem item : document.getItems()) {
            item.setCount(stock.getOrDefault(item.getProduct().getId(), 0), item.getCountedQuantity(),
                    item.getReason(), item.getReasonNote());
        }
        document.setBookSnapshotAt(Instant.now());
        return toResponse(stockDocumentRepository.saveAndFlush(document));
    }

    /**
     * BR-CNT-04..06: mọi dòng đã đếm, dòng lệch có lý do đang dùng → ghi chênh cho từng dòng lệch trong MỘT transaction
     * (thừa IN, thiếu OUT; khớp không ghi gì). Dòng thiếu mà tồn hiện tại không đủ trừ → từ chối, rollback hết.
     * SP phát sinh sau mốc chụp KHÔNG chặn — giao diện đã cảnh báo trước (cách c đã duyệt).
     */
    @Transactional
    public StockCountResponse complete(Long id, String username) {
        StockDocument document = lockForChange(id, DRAFT, "xác nhận");
        checkWarehouseActive(document.getWarehouse());
        List<StockDocumentItem> items = document.getItems();
        if (items.isEmpty()) {
            throw new BusinessException(label(document) + " chưa có dòng nào để xác nhận");
        }
        List<StockDocumentItem> uncounted = items.stream().filter(item -> item.getCountedQuantity() == null).toList();
        if (!uncounted.isEmpty()) {
            throw new BusinessException("Còn " + uncounted.size() + " dòng chưa nhập số đếm: " + productLabels(uncounted)
                    + ". Nhập số đếm, hoặc bỏ dòng nếu không kiểm sản phẩm đó");
        }
        List<StockDocumentItem> missingReason = items.stream()
                .filter(item -> item.getDifference() != 0 && (item.getReason() == null || !item.getReason().isActive()))
                .toList();
        if (!missingReason.isEmpty()) {
            throw new BusinessException("Dòng lệch phải chọn lý do đang dùng: " + productLabels(missingReason));
        }

        // Thứ tự product_id tăng dần như mọi nghiệp vụ tồn khác — giảm nguy cơ deadlock
        items.stream()
                .filter(item -> item.getDifference() != 0)
                .sorted(Comparator.comparing((StockDocumentItem item) -> item.getProduct().getId()))
                .forEach(item -> {
                    if (item.getDifference() > 0) {
                        inventoryService.stockIn(item, document.getWarehouse(), item.getQuantity());
                    } else {
                        inventoryService.stockOut(item, document.getWarehouse(), item.getQuantity());
                    }
                });

        document.markCompleted(Instant.now(), username);
        return toResponse(stockDocumentRepository.saveAndFlush(document));
    }

    @Transactional
    public StockCountResponse cancel(Long id, String reason) {
        StockDocument document = lockForChange(id, DRAFT, "hủy");
        document.markCancelled(blankToNull(reason), Instant.now());
        return toResponse(stockDocumentRepository.saveAndFlush(document));
    }

    /** BR-CNT-08: đảo toàn phiếu (lý do bắt buộc), mỗi phiếu một lần; không đủ tồn để trừ lại phần thừa → từ chối. */
    @Transactional
    public StockCountResponse reverse(Long id, String reason) {
        StockDocument document = lockForChange(id, COMPLETED, "đảo");
        stockTransactionRepository.findOriginalsOfDocument(document.getId()).forEach(inventoryService::reverse);
        document.markReversed(reason.trim(), Instant.now());
        return toResponse(stockDocumentRepository.saveAndFlush(document));
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

    /** Lý do được gửi phải tồn tại (404). Ngừng dùng thì vẫn lưu nháp được, chặn lúc xác nhận. */
    private Map<Long, StockCountReason> loadReasons(List<StockCountLineRequest> lines) {
        Set<Long> ids = lines.stream().map(StockCountLineRequest::reasonId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, StockCountReason> reasons = stockCountReasonRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(StockCountReason::getId, Function.identity()));
        ids.stream().filter(reasonId -> !reasons.containsKey(reasonId)).findFirst().ifPresent(reasonId -> {
            throw new ResourceNotFoundException("Không tìm thấy lý do lệch id=" + reasonId);
        });
        return reasons;
    }

    private Map<Long, Integer> currentStock(Warehouse warehouse, List<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return inventoryRepository.findQuantities(warehouse.getId(), productIds).stream()
                .collect(Collectors.toMap(QuantityByKey::getId, row -> row.getQuantity().intValue()));
    }

    private StockCountResponse toResponse(StockDocument document) {
        List<Long> moved = List.of();
        if (document.getStatus() == DRAFT && !document.getItems().isEmpty()) {
            moved = stockTransactionRepository.findProductIdsMovedSince(document.getWarehouse().getId(),
                    document.getItems().stream().map(item -> item.getProduct().getId()).toList(),
                    document.getBookSnapshotAt());
        }
        return StockCountResponse.from(document, moved);
    }

    // BR-CNT-02: mỗi SP một dòng
    private static List<Long> distinctProductIds(List<StockCountLineRequest> lines) {
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < lines.size(); i++) {
            if (!seen.add(lines.get(i).productId())) {
                throw new InvalidRequestException("lines[" + i + "].productId", "Sản phẩm này đã có ở một dòng khác của phiếu");
            }
        }
        return lines.stream().map(StockCountLineRequest::productId).toList();
    }

    private static void checkWarehouseActive(Warehouse warehouse) {
        if (!warehouse.isActive()) {
            throw new BusinessException("Kho '" + warehouse.getName() + "' (" + warehouse.getCode()
                    + ") đang ngừng hoạt động, không kiểm kê được");
        }
    }

    // BR-CNT-01: mặc định hôm nay theo giờ Việt Nam, lùi được, không ở tương lai
    private static LocalDate resolveDocumentDate(LocalDate requested) {
        LocalDate today = LocalDate.now(CommonSpecifications.BUSINESS_ZONE);
        if (requested == null) {
            return today;
        }
        if (requested.isAfter(today)) {
            throw new InvalidRequestException("documentDate", "Ngày kiểm kê không được ở tương lai");
        }
        return requested;
    }

    private static String productLabels(List<StockDocumentItem> items) {
        return items.stream()
                .map(item -> "'" + item.getProduct().getName() + "' (" + item.getProduct().getCode() + ")")
                .collect(Collectors.joining(", "));
    }

    private static String label(StockDocument document) {
        return "Phiếu kiểm kê " + document.getCode();
    }

    private static ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException("Không tìm thấy phiếu kiểm kê id=" + id);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
