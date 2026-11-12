package com.grainwarehouse.transfer.service;

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
import com.grainwarehouse.product.entity.Product;
import com.grainwarehouse.product.repository.ProductRepository;
import com.grainwarehouse.transfer.dto.TransferLineRequest;
import com.grainwarehouse.transfer.dto.TransferRequest;
import com.grainwarehouse.transfer.dto.TransferResponse;
import com.grainwarehouse.transfer.dto.TransferSearchCriteria;
import com.grainwarehouse.transfer.dto.TransferSummaryResponse;
import com.grainwarehouse.transfer.repository.TransferSpecifications;
import com.grainwarehouse.warehouse.entity.Warehouse;
import com.grainwarehouse.warehouse.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
 * Phiếu điều chuyển kho (BR-TRF-01..12): dời hàng từ kho nguồn sang kho đích trong hệ thống.
 *
 * <p>Cùng vòng đời với phiếu nhập — Nháp → Hoàn tất → Đã đảo, Nháp → Đã hủy — và cùng cách bảo vệ trạng thái:
 * mọi thao tác thay đổi phiếu bắt đầu bằng {@link #lockForChange} (khóa FOR UPDATE rồi mới kiểm tra, BR-IMP-14).
 *
 * <p>Điểm riêng: hoàn tất một dòng phiếu đổi tồn ở HAI kho — OUT tại kho nguồn, IN tại kho đích — và cả hai
 * (cùng mọi dòng khác của phiếu) nằm trong MỘT transaction (BR-TRF-01..03). Không có đường nào để chỉ một kho
 * được cập nhật: {@link InventoryService} bắt buộc chạy trong transaction của hàm gọi (Propagation.MANDATORY),
 * một lỗi bất kỳ → rollback cả phiếu.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransferService {

    private static final StockDocumentType TYPE = StockDocumentType.TRANSFER;
    private static final String CODE_FORMAT = "DC%06d";

    private final StockDocumentRepository stockDocumentRepository;
    private final StockDocumentItemRepository stockDocumentItemRepository;
    private final StockTransactionRepository stockTransactionRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;

    public PageResponse<TransferSummaryResponse> list(TransferSearchCriteria criteria, Pageable pageable) {
        Page<StockDocument> page = stockDocumentRepository.findAll(TransferSpecifications.from(criteria), pageable);
        // Số dòng, tổng SL: một câu GROUP BY cho cả trang thay vì tải dòng hàng của từng phiếu
        List<Long> ids = page.getContent().stream().map(StockDocument::getId).toList();
        Map<Long, DocumentLineTotals> totals = ids.isEmpty() ? Map.of()
                : stockDocumentItemRepository.sumByDocument(ids).stream()
                        .collect(Collectors.toMap(DocumentLineTotals::getDocumentId, Function.identity()));
        return PageResponse.from(page, document -> TransferSummaryResponse.from(document, totals.get(document.getId())));
    }

    public TransferResponse get(Long id) {
        return TransferResponse.from(stockDocumentRepository.findWithDetailsByIdAndType(id, TYPE)
                .orElseThrow(() -> notFound(id)));
    }

    /** BR-TRF-08: tạo Nháp — chưa ảnh hưởng tồn, không giữ chỗ tồn (lập nháp vượt tồn vẫn được). */
    @Transactional
    public TransferResponse create(TransferRequest request, String username) {
        References refs = resolve(request);
        String code = CODE_FORMAT.formatted(stockDocumentRepository.nextTransferCodeNumber());
        StockDocument document = new StockDocument(TYPE, code, username);
        apply(document, request, refs);
        return TransferResponse.from(stockDocumentRepository.save(document));
    }

    @Transactional
    public TransferResponse update(Long id, TransferRequest request) {
        StockDocument document = lockForChange(id, DRAFT, "sửa");
        References refs = resolve(request);
        apply(document, request, refs);
        return TransferResponse.from(stockDocumentRepository.saveAndFlush(document));
    }

    /**
     * Hoàn tất: trừ kho nguồn, cộng kho đích, ghi hai StockTransaction cho mỗi dòng — tất cả trong MỘT transaction.
     * Một dòng thiếu tồn → BusinessException → rollback hết, phiếu vẫn là Nháp (BR-TRF-02, 03).
     *
     * <p>Thứ tự khóa (BR-TRF-04): mỗi lần cộng/trừ khóa một dòng inventory (SP, kho) đến hết transaction. Hai phiếu
     * K1→K2 và K2→K1 chạy cùng lúc, nếu mỗi phiếu đều "trừ nguồn trước, cộng đích sau" thì phiếu A giữ dòng ở K1
     * chờ K2, phiếu B giữ dòng ở K2 chờ K1 → deadlock. Ở đây mọi phiếu đều đụng kho có id NHỎ hơn trước, trong mỗi
     * kho theo product_id tăng dần → hai phiếu luôn xếp hàng ở cùng một dòng đầu tiên, không ai giữ thứ người kia cần.
     * Hệ quả: khi kho đích có id nhỏ hơn, hàng được CỘNG vào đích trước khi TRỪ ở nguồn — vẫn đúng, vì nếu bước
     * trừ thất bại thì bước cộng bị rollback cùng transaction.
     */
    @Transactional
    public TransferResponse complete(Long id, String username) {
        StockDocument document = lockForChange(id, DRAFT, "hoàn tất");
        Warehouse source = document.getWarehouse();
        Warehouse target = document.getTargetWarehouse();
        checkTargetUsable(target);

        List<StockDocumentItem> items = document.getItems().stream()
                .sorted(Comparator.comparing((StockDocumentItem item) -> item.getProduct().getId()))
                .toList();
        Runnable takeFromSource = () -> items.forEach(item -> inventoryService.stockOut(item, source, item.getQuantity()));
        Runnable putIntoTarget = () -> items.forEach(item -> inventoryService.stockIn(item, target, item.getQuantity()));
        if (source.getId() < target.getId()) {
            takeFromSource.run();
            putIntoTarget.run();
        } else {
            putIntoTarget.run();
            takeFromSource.run();
        }

        document.markCompleted(Instant.now(), username);
        return TransferResponse.from(stockDocumentRepository.saveAndFlush(document));
    }

    @Transactional
    public TransferResponse cancel(Long id, String reason) {
        StockDocument document = lockForChange(id, DRAFT, "hủy");
        document.markCancelled(blankToNull(reason), Instant.now());
        return TransferResponse.from(stockDocumentRepository.saveAndFlush(document));
    }

    /**
     * BR-TRF-12: đảo toàn phiếu, mỗi phiếu một lần — hàng quay về kho nguồn. Mỗi giao dịch gốc có một giao dịch
     * ngược chiều trỏ về nó (IN ở kho đích → OUT ở kho đích, OUT ở kho nguồn → IN ở kho nguồn). Kho đích đã
     * bán bớt, không đủ để trả về → từ chối, rollback hết (BR-INV-01). Cùng thứ tự khóa với lúc hoàn tất.
     */
    @Transactional
    public TransferResponse reverse(Long id, String reason) {
        StockDocument document = lockForChange(id, COMPLETED, "đảo");
        stockTransactionRepository.findOriginalsOfDocument(document.getId()).stream()
                .sorted(Comparator.comparing((StockTransaction transaction) -> transaction.getWarehouse().getId())
                        .thenComparing(transaction -> transaction.getProduct().getId()))
                .forEach(inventoryService::reverse);
        document.markReversed(reason.trim(), Instant.now());
        return TransferResponse.from(stockDocumentRepository.saveAndFlush(document));
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
    private References resolve(TransferRequest request) {
        LocalDate documentDate = resolveDocumentDate(request.documentDate());
        // BR-TRF-05: chuyển từ một kho sang chính nó là vô nghĩa (CHECK ck_stock_document_transfer_distinct ở DB)
        if (request.sourceWarehouseId().equals(request.targetWarehouseId())) {
            throw new InvalidRequestException("targetWarehouseId", "Kho đích phải khác kho nguồn");
        }
        List<Long> productIds = distinctProductIds(request.lines());

        Warehouse source = findWarehouse(request.sourceWarehouseId());
        Warehouse target = findWarehouse(request.targetWarehouseId());
        Map<Long, Product> products = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        productIds.stream().filter(productId -> !products.containsKey(productId)).findFirst().ifPresent(productId -> {
            throw new ResourceNotFoundException("Không tìm thấy sản phẩm id=" + productId);
        });

        checkTargetUsable(target);
        return new References(source, target, products, documentDate);
    }

    private Warehouse findWarehouse(Long id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kho id=" + id));
    }

    // BR-TRF-10: mặc định hôm nay theo giờ Việt Nam, lùi được, không ở tương lai (như BR-IMP-08)
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

    // BR-TRF-05: mỗi SP một dòng
    private static List<Long> distinctProductIds(List<TransferLineRequest> lines) {
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < lines.size(); i++) {
            if (!seen.add(lines.get(i).productId())) {
                throw new InvalidRequestException("lines[" + i + "].productId",
                        "Sản phẩm này đã có ở một dòng khác của phiếu");
            }
        }
        return lines.stream().map(TransferLineRequest::productId).toList();
    }

    /**
     * BR-TRF-09: chỉ kho ĐÍCH phải đang hoạt động — kiểm tra cả lúc lưu nháp lẫn lúc hoàn tất (kho có thể bị ngừng
     * ở giữa). Kho NGUỒN đã ngừng vẫn chuyển đi được: đó chính là cách rút hết hàng khỏi kho đóng cửa (BR-STK-01).
     * SP đã ngừng kinh doanh cũng chuyển được — hàng còn tồn vẫn phải dời.
     */
    private static void checkTargetUsable(Warehouse target) {
        if (!target.isActive()) {
            throw new BusinessException("Kho đích '" + target.getName() + "' (" + target.getCode()
                    + ") đang ngừng hoạt động, không thể điều chuyển hàng đến");
        }
    }

    /** Dòng được cập nhật tại chỗ theo productId — xem InboundReceiptService.apply. Điều chuyển không có giá. */
    private static void apply(StockDocument document, TransferRequest request, References refs) {
        document.setWarehouse(refs.source());
        document.setTargetWarehouse(refs.target());
        document.setDocumentDate(refs.documentDate());
        document.setNote(blankToNull(request.note()));

        Set<Long> requestedIds = refs.products().keySet();
        document.removeItemsIf(item -> !requestedIds.contains(item.getProduct().getId()));
        Map<Long, StockDocumentItem> existing = document.getItems().stream()
                .collect(Collectors.toMap(item -> item.getProduct().getId(), Function.identity()));

        for (TransferLineRequest line : request.lines()) {
            StockDocumentItem item = existing.get(line.productId());
            if (item == null) {
                item = new StockDocumentItem(refs.products().get(line.productId()));
                document.addItem(item);
            }
            item.setValues(null, line.quantity(), BigDecimal.ZERO);
        }
        document.recalculateTotal();
    }

    private static String label(StockDocument document) {
        return "Phiếu điều chuyển " + document.getCode();
    }

    private static ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException("Không tìm thấy phiếu điều chuyển id=" + id);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record References(Warehouse source, Warehouse target, Map<Long, Product> products, LocalDate documentDate) {
    }
}
