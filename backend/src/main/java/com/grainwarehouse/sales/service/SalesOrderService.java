package com.grainwarehouse.sales.service;

import com.grainwarehouse.common.exception.BusinessException;
import com.grainwarehouse.common.exception.InvalidRequestException;
import com.grainwarehouse.common.exception.ResourceNotFoundException;
import com.grainwarehouse.common.response.PageResponse;
import com.grainwarehouse.common.search.CommonSpecifications;
import com.grainwarehouse.customer.entity.Customer;
import com.grainwarehouse.customer.repository.CustomerRepository;
import com.grainwarehouse.inventory.entity.StockDocument;
import com.grainwarehouse.inventory.entity.StockDocumentItem;
import com.grainwarehouse.inventory.entity.StockDocumentStatus;
import com.grainwarehouse.inventory.entity.StockDocumentType;
import com.grainwarehouse.inventory.repository.InventoryRepository;
import com.grainwarehouse.inventory.repository.StockDocumentRepository;
import com.grainwarehouse.inventory.repository.StockTransactionRepository;
import com.grainwarehouse.inventory.service.InventoryService;
import com.grainwarehouse.product.entity.Product;
import com.grainwarehouse.product.repository.ProductRepository;
import com.grainwarehouse.sales.dto.PendingOrderRequest;
import com.grainwarehouse.sales.dto.PriceAdjustmentRequest;
import com.grainwarehouse.sales.dto.RecordPaymentRequest;
import com.grainwarehouse.sales.dto.SaleRequestLineRequest;
import com.grainwarehouse.sales.dto.SalesOrderLineRequest;
import com.grainwarehouse.sales.dto.SalesOrderRequest;
import com.grainwarehouse.sales.dto.SalesOrderResponse;
import com.grainwarehouse.sales.dto.SalesOrderSearchCriteria;
import com.grainwarehouse.sales.dto.SalesOrderSummaryResponse;
import com.grainwarehouse.sales.entity.OrderChannel;
import com.grainwarehouse.sales.entity.PaymentMethod;
import com.grainwarehouse.sales.entity.SalePayment;
import com.grainwarehouse.sales.entity.SalePaymentType;
import com.grainwarehouse.sales.entity.SalePriceAdjustment;
import com.grainwarehouse.sales.entity.SaleRequestItem;
import com.grainwarehouse.sales.repository.SalePaymentRepository;
import com.grainwarehouse.sales.repository.SalePriceAdjustmentRepository;
import com.grainwarehouse.sales.repository.SaleRequestItemRepository;
import com.grainwarehouse.sales.repository.SalesOrderSpecifications;
import com.grainwarehouse.warehouse.entity.Warehouse;
import com.grainwarehouse.warehouse.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.grainwarehouse.inventory.entity.StockDocumentStatus.COMPLETED;
import static com.grainwarehouse.inventory.entity.StockDocumentStatus.DRAFT;
import static com.grainwarehouse.inventory.entity.StockDocumentStatus.PENDING;
import static com.grainwarehouse.inventory.entity.StockDocumentStatus.REVERSED;

/**
 * Phiếu bán / xuất kho và thanh toán của phiếu (BR-SAL-01..15), kể cả đơn khách đặt Chờ xác nhận (BR-SAL-16..22).
 *
 * <p>Cùng quy trình bảo vệ trạng thái với phiếu nhập (BR-SAL-04 → BR-IMP-14): mọi thao tác làm thay đổi
 * phiếu — sửa, hoàn tất, hủy, đảo, thu tiền, hoàn khoản thu — đều bắt đầu bằng {@link #lockForChange}.
 * Thu tiền cũng phải khóa phiếu: hai người cùng thu 6 triệu cho phiếu còn nợ 10 triệu, không khóa thì
 * cả hai đều thấy "đã thu 0" và cùng ghi paid_amount = 6 triệu (Hibernate ghi giá trị tuyệt đối) →
 * nhật ký có 12 triệu nhưng snapshot chỉ 6 triệu (lost update) — CHECK paid <= total KHÔNG bắt được.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesOrderService {

    private static final StockDocumentType TYPE = StockDocumentType.OUTBOUND;
    private static final String CODE_FORMAT = "PX%06d";
    private static final Locale VIETNAMESE = Locale.forLanguageTag("vi-VN");
    private static final String APP_CREATOR_PREFIX = "app:";
    /** Tiền tố lý do khi khách tự hủy trên app — app khách chỉ hiện lý do hủy bắt đầu bằng chuỗi này (BR-POR-11). */
    public static final String APP_CANCEL_REASON = "Khách hủy qua app";

    private final StockDocumentRepository stockDocumentRepository;
    private final StockTransactionRepository stockTransactionRepository;
    private final SalePaymentRepository salePaymentRepository;
    private final SaleRequestItemRepository saleRequestItemRepository;
    private final SalePriceAdjustmentRepository salePriceAdjustmentRepository;
    private final WarehouseRepository warehouseRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final InventoryRepository inventoryRepository;

    public PageResponse<SalesOrderSummaryResponse> list(SalesOrderSearchCriteria criteria, Pageable pageable) {
        return PageResponse.from(
                stockDocumentRepository.findAll(SalesOrderSpecifications.from(criteria), pageable),
                SalesOrderSummaryResponse::from);
    }

    public SalesOrderResponse get(Long id) {
        return toResponse(stockDocumentRepository.findWithDetailsByIdAndType(id, TYPE)
                .orElseThrow(() -> notFound(id)));
    }

    /** BR-SAL-02: kho lập phiếu Nháp trực tiếp — chưa ảnh hưởng tồn, không giữ chỗ tồn. */
    @Transactional
    public SalesOrderResponse create(SalesOrderRequest request, String username) {
        References refs = resolve(request);
        StockDocument document = new StockDocument(TYPE, nextCode(), username);
        document.setOrderChannel(OrderChannel.DIRECT);
        apply(document, request, refs);
        return toResponse(stockDocumentRepository.save(document));
    }

    @Transactional
    public SalesOrderResponse update(Long id, SalesOrderRequest request) {
        StockDocument document = lockForChange(id, DRAFT, "sửa");
        // Phiếu lập từ đơn khách đặt giữ nguyên khách — phần khách đặt (BR-SAL-20) phải khớp người mua
        if (document.getConfirmedAt() != null) {
            requireSameCustomer(document, request.customerId());
        }
        References refs = resolve(request);
        apply(document, request, refs);
        return toResponse(stockDocumentRepository.saveAndFlush(document));
    }

    /**
     * BR-SAL-17: ghi đơn khách đặt — phiếu bán Chờ xác nhận, chỉ có khách và SP + SL khách đặt.
     * Chưa có kho, giá, dòng phiếu; không ảnh hưởng tồn. Có mã PX ngay để báo khách.
     */
    @Transactional
    public SalesOrderResponse createPending(PendingOrderRequest request, String username) {
        RequestReferences refs = resolve(request, false);
        StockDocument document = StockDocument.pendingSale(nextCode(), username);
        document.setDocumentDate(today());
        applyHeader(document, request, refs.customer());
        stockDocumentRepository.save(document);
        replaceRequestLines(document, request.lines(), refs.products());
        return toResponse(document);
    }

    /**
     * BR-SAL-18: chỉ đơn Chờ xác nhận được sửa phần khách đặt; không đổi khách.
     * Đơn khách đặt qua app giữ kênh App (BR-POR-11): không đổi sang kênh khác, cũng không gán App cho đơn kho ghi.
     */
    @Transactional
    public SalesOrderResponse updatePending(Long id, PendingOrderRequest request) {
        StockDocument document = lockForChange(id, PENDING, "sửa phần khách đặt");
        requireSameCustomer(document, request.customerId());
        boolean appOrder = document.getOrderChannel() == OrderChannel.APP;
        if (appOrder && request.orderChannel() != OrderChannel.APP) {
            throw new InvalidRequestException("orderChannel", "Đơn khách đặt qua app giữ kênh App, không đổi được");
        }
        RequestReferences refs = resolve(request, appOrder);
        applyHeader(document, request, refs.customer());
        replaceRequestLines(document, request.lines(), refs.products());
        return toResponse(stockDocumentRepository.saveAndFlush(document));
    }

    // ===== Đơn khách tự đặt qua app (BR-POR-07, 09) — customerId luôn lấy từ token, không từ request =====

    /**
     * BR-POR-07: như createPending nhưng kênh APP; người lập ghi "app:<mã khách>".
     * customerNote = lời khách → cột customer_note; ghi chú nội bộ (note) để trống cho cửa hàng (BR-POR-11).
     */
    @Transactional
    public SalesOrderResponse createAppOrder(Long customerId, LocalDate deliveryDate, String customerNote,
                                             List<SaleRequestLineRequest> lines) {
        PendingOrderRequest request = new PendingOrderRequest(customerId, OrderChannel.APP, deliveryDate, null, lines);
        RequestReferences refs = resolve(request, true);
        StockDocument document = StockDocument.pendingSale(nextCode(), APP_CREATOR_PREFIX + refs.customer().getCode());
        document.setDocumentDate(today());
        applyAppHeader(document, refs.customer(), deliveryDate, customerNote);
        stockDocumentRepository.save(document);
        replaceRequestLines(document, request.lines(), refs.products());
        return toResponse(document);
    }

    /**
     * BR-POR-09: khách sửa đơn của mình khi còn Chờ xác nhận. Đơn của khách khác → 404 như không tồn tại.
     * Chỉ đổi lời khách — ghi chú nội bộ cửa hàng đã ghi (nếu có) giữ nguyên (BR-POR-11).
     */
    @Transactional
    public SalesOrderResponse updateAppOrder(Long customerId, Long id, LocalDate deliveryDate, String customerNote,
                                             List<SaleRequestLineRequest> lines) {
        StockDocument document = lockOwnedPending(id, customerId, "sửa");
        PendingOrderRequest request = new PendingOrderRequest(customerId, OrderChannel.APP, deliveryDate, null, lines);
        RequestReferences refs = resolve(request, true);
        applyAppHeader(document, refs.customer(), deliveryDate, customerNote);
        replaceRequestLines(document, request.lines(), refs.products());
        return toResponse(stockDocumentRepository.saveAndFlush(document));
    }

    /** BR-POR-09: khách tự hủy khi còn Chờ xác nhận; lý do ghi tự động để thỏa BR-SAL-21. */
    @Transactional
    public SalesOrderResponse cancelAppOrder(Long customerId, Long id, String note) {
        StockDocument document = lockOwnedPending(id, customerId, "hủy");
        String customerNote = blankToNull(note);
        document.markCancelled(customerNote == null ? APP_CANCEL_REASON : APP_CANCEL_REASON + ": " + customerNote,
                Instant.now());
        return toResponse(stockDocumentRepository.saveAndFlush(document));
    }

    /**
     * Khóa trước, kiểm CHỦ ĐƠN rồi mới kiểm trạng thái: đơn của khách khác trả đúng thông báo "không tìm thấy" như
     * đơn không tồn tại — không lộ ra đơn đó có thật hay đang ở trạng thái nào.
     */
    private StockDocument lockOwnedPending(Long id, Long customerId, String action) {
        StockDocument document = stockDocumentRepository.findForUpdate(id, TYPE)
                .filter(found -> found.getCustomer().getId().equals(customerId))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng id=" + id));
        if (document.getStatus() != PENDING) {
            throw new BusinessException("Đơn " + document.getCode() + " đã được cửa hàng tiếp nhận ("
                    + document.getStatus().label() + "), không " + action + " trên app được nữa. Vui lòng gọi cửa hàng");
        }
        return document;
    }

    /**
     * BR-SAL-19: xác nhận đơn khách đặt — Chờ xác nhận → Nháp. Kho gửi kho xuất, ngày bán và dòng hàng có giá
     * (được đổi SL, đổi SP so với khách đặt). Áp dụng đủ kiểm tra của phiếu nháp (BR-SAL-01, 04, 15).
     * Phần khách đặt giữ nguyên (BR-SAL-20). Chưa trừ tồn — trừ khi Hoàn tất (BR-SAL-02).
     * Khóa phiếu trước: hai người cùng xác nhận thì người sau thấy trạng thái Nháp và bị từ chối (BR-SAL-21).
     */
    @Transactional
    public SalesOrderResponse confirm(Long id, SalesOrderRequest request, String username) {
        StockDocument document = lockForChange(id, PENDING, "xác nhận");
        requireSameCustomer(document, request.customerId());
        References refs = resolve(request);
        apply(document, request, refs);
        document.markConfirmed(Instant.now(), username);
        return toResponse(stockDocumentRepository.saveAndFlush(document));
    }

    /**
     * BR-SAL-02: trừ tồn mọi dòng và ghi StockTransaction OUT, trong một transaction.
     * Một dòng thiếu tồn → BusinessException → rollback cả các dòng đã trừ, phiếu vẫn là Nháp.
     */
    @Transactional
    public SalesOrderResponse complete(Long id) {
        StockDocument document = lockForChange(id, DRAFT, "hoàn tất");
        // BR-SAL-15: kiểm tra lại — khách/kho/SP có thể đã ngừng từ lúc lưu nháp
        checkUsable(document.getWarehouse(), document.getCustomer(),
                document.getItems().stream().map(StockDocumentItem::getProduct).toList());

        // Trừ theo product_id tăng dần — cùng thứ tự khóa dòng tồn với mọi nghiệp vụ khác (giảm deadlock)
        document.getItems().stream()
                .sorted(Comparator.comparing((StockDocumentItem item) -> item.getProduct().getId()))
                .forEach(item -> inventoryService.stockOut(item, document.getWarehouse(), item.getQuantity()));

        document.markCompleted(Instant.now());
        return toResponse(stockDocumentRepository.saveAndFlush(document));
    }

    /**
     * BR-DLV-06: xuất chuyến — hoàn tất (trừ tồn) mọi phiếu của chuyến trong transaction CỦA NGƯỜI GỌI (MANDATORY:
     * gọi ngoài transaction thì lỗi ngay). Một phiếu thiếu hàng → BusinessException → rollback cả chuyến, không phiếu
     * nào bị trừ tồn lẻ (quy tắc cứng 1, 4). Cùng kiểm tra như {@link #complete}: phiếu phải còn Nháp, kho / khách / SP
     * còn hoạt động (BR-SAL-15).
     *
     * <p>Thứ tự khóa: phiếu theo id tăng dần, rồi dòng tồn theo (SP, kho) tăng dần trên TOÀN chuyến — trùng thứ tự
     * với Hoàn tất một phiếu (SP tăng dần trong một kho), nên hai thao tác chạy cùng lúc không khóa chéo nhau.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void completeForDelivery(Collection<Long> ids, String tripCode) {
        List<StockDocument> documents = ids.stream().sorted()
                .map(id -> stockDocumentRepository.findForUpdate(id, TYPE).orElseThrow(() -> notFound(id)))
                .toList();
        List<String> notDraft = documents.stream().filter(document -> document.getStatus() != DRAFT)
                .map(document -> document.getCode() + " (" + document.getStatus().label() + ")").toList();
        if (!notDraft.isEmpty()) {
            throw new BusinessException("Chuyến " + tripCode + " có phiếu không còn là Nháp: "
                    + String.join(", ", notDraft) + ". Bỏ các phiếu này khỏi chuyến rồi xuất lại");
        }
        documents.forEach(document -> checkUsable(document.getWarehouse(), document.getCustomer(),
                document.getItems().stream().map(StockDocumentItem::getProduct).toList()));
        requireEnoughStock(documents, tripCode);

        documents.stream().flatMap(document -> document.getItems().stream())
                .sorted(Comparator.comparing((StockDocumentItem item) -> item.getProduct().getId())
                        .thenComparing(item -> item.getDocument().getWarehouse().getId())
                        .thenComparing(item -> item.getDocument().getId()))
                .forEach(item -> inventoryService.stockOut(item, item.getDocument().getWarehouse(), item.getQuantity()));

        Instant now = Instant.now();
        documents.forEach(document -> document.markCompleted(now));
        stockDocumentRepository.saveAllAndFlush(documents);
    }

    /**
     * Báo thiếu hàng theo TỔNG của cả chuyến: hai phiếu cùng lấy cám heo ở kho A (30 + 40) mà kho còn 50 thì báo
     * "còn 50, chuyến cần 70 (PX1: 30, PX2: 40)". Không có bước này, lỗi trừ tồn của phiếu thứ hai sẽ ghi "còn 20,
     * cần trừ 40" — số 20 là tồn sau khi đã trừ phiếu thứ nhất, sẽ rollback, người dùng nhìn kho thấy 50 không hiểu.
     * Đây chỉ là kiểm tra để báo lỗi dễ hiểu; chặn tồn âm thật vẫn là UPDATE có điều kiện trong InventoryService.
     */
    private void requireEnoughStock(List<StockDocument> documents, String tripCode) {
        record Key(Product product, Warehouse warehouse) {
        }
        Map<Key, List<StockDocumentItem>> demand = documents.stream().flatMap(document -> document.getItems().stream())
                .collect(Collectors.groupingBy(item -> new Key(item.getProduct(), item.getDocument().getWarehouse()),
                        LinkedHashMap::new, Collectors.toList()));
        List<String> shortages = new ArrayList<>();
        demand.forEach((key, items) -> {
            int needed = items.stream().mapToInt(StockDocumentItem::getQuantity).sum();
            int current = inventoryRepository.findQuantity(key.product().getId(), key.warehouse().getId()).orElse(0);
            if (current < needed) {
                String breakdown = items.size() == 1 ? "" : " (" + items.stream()
                        .map(item -> item.getDocument().getCode() + ": " + item.getQuantity())
                        .collect(Collectors.joining(", ")) + ")";
                shortages.add("'" + key.product().getName() + "' (" + key.product().getCode() + ") tại kho '"
                        + key.warehouse().getName() + "' còn " + current + ", chuyến cần " + needed + breakdown);
            }
        });
        if (!shortages.isEmpty()) {
            throw new BusinessException("Không đủ tồn để xuất chuyến " + tripCode + ": " + String.join("; ", shortages));
        }
    }

    /** Hủy phiếu Nháp (lý do tùy chọn) hoặc đơn Chờ xác nhận (lý do bắt buộc — BR-SAL-21). */
    @Transactional
    public SalesOrderResponse cancel(Long id, String reason) {
        StockDocument document = lockForChange(id, Set.of(PENDING, DRAFT), "hủy");
        String cleanReason = blankToNull(reason);
        if (document.getStatus() == PENDING && cleanReason == null) {
            throw new InvalidRequestException("reason", "Hủy đơn khách đặt phải ghi lý do");
        }
        document.markCancelled(cleanReason, Instant.now());
        return toResponse(stockDocumentRepository.saveAndFlush(document));
    }

    /**
     * BR-SAL-05: đảo toàn phiếu (giao sai, hàng không đạt) — mỗi giao dịch OUT gốc có một IN trỏ về nó,
     * hàng quay lại đúng kho đã xuất. BR-SAL-14: phiếu đã thu tiền thì phải hoàn các khoản thu trước.
     */
    @Transactional
    public SalesOrderResponse reverse(Long id, String reason) {
        StockDocument document = lockForChange(id, COMPLETED, "đảo");
        if (document.getPaidAmount().signum() > 0) {
            throw new BusinessException(label(document) + " đã thu " + money(document.getPaidAmount())
                    + ". Hãy hoàn các khoản đã thu trước khi đảo phiếu");
        }
        stockTransactionRepository.findOriginalsOfDocument(document.getId()).forEach(inventoryService::reverse);
        document.markReversed(reason.trim(), Instant.now());
        return toResponse(stockDocumentRepository.saveAndFlush(document));
    }

    /**
     * BR-SAL-23..26: điều chỉnh giá phiếu đã giao (Hoàn tất) mà khách chưa trả đồng nào. Chỉ đổi đơn giá —
     * số lượng không đổi nên không động tới tồn kho / StockTransaction. Mỗi dòng đổi giá ghi một dòng nhật ký
     * bất biến. Dòng gửi lên trùng giá hiện tại thì bỏ qua; không dòng nào đổi → 400.
     * Khóa phiếu trước rồi mới đọc "đã thu": người khác đang thu tiền cùng lúc thì một trong hai phải chờ.
     */
    @Transactional
    public SalesOrderResponse adjustPrices(Long id, PriceAdjustmentRequest request, String username) {
        StockDocument document = lockForChange(id, COMPLETED, "điều chỉnh giá");
        if (document.getPaidAmount().signum() > 0) {
            throw new BusinessException(label(document) + " đã thu " + money(document.getPaidAmount())
                    + ". Chỉ điều chỉnh giá được khi khách chưa trả đồng nào");
        }
        distinctProductIds(request.lines().stream().map(PriceAdjustmentRequest.Line::productId).toList());
        Map<Long, StockDocumentItem> items = document.getItems().stream()
                .collect(Collectors.toMap(item -> item.getProduct().getId(), Function.identity()));

        String reason = request.reason().trim();
        List<SalePriceAdjustment> adjustments = new ArrayList<>();
        for (int i = 0; i < request.lines().size(); i++) {
            PriceAdjustmentRequest.Line line = request.lines().get(i);
            StockDocumentItem item = items.get(line.productId());
            if (item == null) {
                throw new InvalidRequestException("lines[" + i + "].productId", "Sản phẩm này không có trên phiếu");
            }
            // compareTo: 300000 và 300000.00 là cùng một giá
            if (item.getUnitPrice().compareTo(line.unitPrice()) == 0) {
                continue;
            }
            BigDecimal oldPrice = item.getUnitPrice();
            BigDecimal oldAmount = item.getLineAmount();
            item.changeUnitPrice(line.unitPrice());
            adjustments.add(SalePriceAdjustment.of(item, oldPrice, oldAmount, reason, username));
        }
        if (adjustments.isEmpty()) {
            throw new InvalidRequestException("lines", "Giá mới trùng giá hiện tại — không có dòng nào thay đổi");
        }

        document.recalculateTotal();
        stockDocumentRepository.saveAndFlush(document);
        salePriceAdjustmentRepository.saveAll(adjustments);
        return toResponse(document);
    }

    /**
     * BR-SAL-09, 11, 12: thu tiền cho phiếu Hoàn tất, tiền mặt và/hoặc chuyển khoản — mỗi hình thức một dòng,
     * cùng transaction. Tổng lần thu không được vượt số còn nợ.
     */
    @Transactional
    public SalesOrderResponse recordPayment(Long id, RecordPaymentRequest request) {
        BigDecimal cash = zeroIfNull(request.cashAmount());
        BigDecimal transfer = zeroIfNull(request.transferAmount());
        BigDecimal total = cash.add(transfer);
        if (total.signum() == 0) {
            throw new InvalidRequestException("cashAmount", "Phải nhập số tiền mặt hoặc chuyển khoản lớn hơn 0");
        }

        StockDocument document = lockForChange(id, COMPLETED, "thu tiền");
        // BR-SAL-12: ngày thu chọn tự do (kể cả trước ngày bán hoặc tương lai); không gửi = hôm nay
        LocalDate paymentDate = request.paymentDate() == null ? today() : request.paymentDate();
        if (total.compareTo(document.getOutstandingAmount()) > 0) {
            throw new BusinessException("Số tiền thu " + money(total) + " vượt số còn nợ "
                    + money(document.getOutstandingAmount()) + " của " + label(document).toLowerCase());
        }

        String note = blankToNull(request.note());
        List<SalePayment> payments = new ArrayList<>();
        if (cash.signum() > 0) {
            payments.add(SalePayment.payment(document, PaymentMethod.CASH, cash, paymentDate, note));
        }
        if (transfer.signum() > 0) {
            payments.add(SalePayment.payment(document, PaymentMethod.BANK_TRANSFER, transfer, paymentDate, note));
        }
        salePaymentRepository.saveAll(payments);
        document.addPaidAmount(total);
        return toResponse(stockDocumentRepository.saveAndFlush(document));
    }

    /**
     * BR-SAL-13: hoàn toàn bộ một khoản thu ghi nhầm bằng dòng REVERSAL trỏ về nó. Mỗi khoản chỉ hoàn một lần
     * (UNIQUE reversal_of_id là tuyến phòng thủ cuối). Ngày hoàn = hôm nay.
     */
    @Transactional
    public SalesOrderResponse reversePayment(Long id, Long paymentId, String reason) {
        StockDocument document = lockForChange(id, COMPLETED, "hoàn khoản thu");
        SalePayment original = salePaymentRepository.findByIdAndDocumentId(paymentId, id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy khoản thu id=" + paymentId + " của " + label(document).toLowerCase()));
        if (original.getType() != SalePaymentType.PAYMENT) {
            throw new BusinessException("Chỉ hoàn được khoản thu, không hoàn một dòng hoàn");
        }
        if (salePaymentRepository.existsByReversalOfId(original.getId())) {
            throw new BusinessException("Khoản thu này đã được hoàn. Mỗi khoản chỉ được hoàn một lần");
        }

        SalePayment reversal = SalePayment.reversalOf(original, today(), reason.trim());
        salePaymentRepository.save(reversal);
        document.addPaidAmount(reversal.signedAmount());
        return toResponse(stockDocumentRepository.saveAndFlush(document));
    }

    /**
     * Cửa duy nhất để thay đổi phiếu (BR-IMP-14 áp dụng cho phiếu bán): khóa trước, kiểm tra trạng thái sau.
     * Phải là lần đọc phiếu ĐẦU TIÊN trong transaction (xem InboundReceiptService.lockForChange).
     */
    private StockDocument lockForChange(Long id, StockDocumentStatus required, String action) {
        return lockForChange(id, Set.of(required), action);
    }

    private StockDocument lockForChange(Long id, Set<StockDocumentStatus> allowed, String action) {
        StockDocument document = stockDocumentRepository.findForUpdate(id, TYPE).orElseThrow(() -> notFound(id));
        if (!allowed.contains(document.getStatus())) {
            throw new BusinessException(statusViolation(document, allowed, action));
        }
        return document;
    }

    private static String statusViolation(StockDocument document, Set<StockDocumentStatus> allowed, String action) {
        if (allowed.contains(COMPLETED) && document.getStatus() == REVERSED && action.equals("đảo")) {
            return label(document) + " đã được đảo. Mỗi phiếu chỉ được đảo một lần";
        }
        // Thứ tự theo vòng đời (Chờ xác nhận, Nháp…) để thông báo đọc tự nhiên
        String allowedLabels = allowed.stream().sorted().map(StockDocumentStatus::label)
                .collect(Collectors.joining(" hoặc "));
        return label(document) + " đang ở trạng thái " + document.getStatus().label()
                + ". Chỉ phiếu " + allowedLabels + " mới được " + action;
    }

    // BR-SAL-18, 19: đơn khách đặt không đổi người mua — ghi nhầm khách thì hủy đơn và ghi lại
    private static void requireSameCustomer(StockDocument document, Long customerId) {
        if (!document.getCustomer().getId().equals(customerId)) {
            throw new InvalidRequestException("customerId",
                    "Không đổi được khách của đơn khách đặt. Ghi nhầm khách thì hủy đơn và ghi lại");
        }
    }

    /** Đọc và kiểm tra mọi thứ request tham chiếu tới, TRƯỚC khi đổi phiếu. Thứ tự: 400 → 404 → 409. */
    private References resolve(SalesOrderRequest request) {
        LocalDate documentDate = resolveDocumentDate(request.documentDate());
        List<Long> productIds = distinctProductIds(
                request.lines().stream().map(SalesOrderLineRequest::productId).toList());

        Warehouse warehouse = warehouseRepository.findById(request.warehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kho id=" + request.warehouseId()));
        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy khách hàng id=" + request.customerId()));
        Map<Long, Product> products = findProducts(productIds);

        checkUsable(warehouse, customer, products.values());
        return new References(warehouse, customer, products, documentDate);
    }

    /** Phần khách đặt: khách + SP (không có kho). Thứ tự: 400 → 404 → 409, như phiếu nháp. */
    private RequestReferences resolve(PendingOrderRequest request, boolean fromApp) {
        // Kênh APP chỉ có ở đường app khách (createAppOrder); kho ghi hộ chỉ chọn Trực tiếp / Điện thoại / Zalo
        if (!fromApp && request.orderChannel() == OrderChannel.APP) {
            throw new InvalidRequestException("orderChannel", "Kênh App chỉ dành cho đơn khách tự đặt qua app");
        }
        List<Long> productIds = distinctProductIds(
                request.lines().stream().map(SaleRequestLineRequest::productId).toList());

        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy khách hàng id=" + request.customerId()));
        Map<Long, Product> products = findProducts(productIds);

        checkUsable(null, customer, products.values());
        return new RequestReferences(customer, products);
    }

    // BR-SAL-04 (như BR-IMP-08): mặc định hôm nay theo giờ VN, lùi ngày được, không ở tương lai
    private static LocalDate resolveDocumentDate(LocalDate requested) {
        LocalDate today = today();
        if (requested == null) {
            return today;
        }
        if (requested.isAfter(today)) {
            throw new InvalidRequestException("documentDate", "Ngày chứng từ không được ở tương lai");
        }
        return requested;
    }

    // Mỗi SP một dòng (như BR-IMP-A05)
    private static List<Long> distinctProductIds(List<Long> productIds) {
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < productIds.size(); i++) {
            if (!seen.add(productIds.get(i))) {
                throw new InvalidRequestException("lines[" + i + "].productId",
                        "Sản phẩm này đã có ở một dòng khác của phiếu");
            }
        }
        return productIds;
    }

    private Map<Long, Product> findProducts(List<Long> productIds) {
        Map<Long, Product> products = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        productIds.stream().filter(productId -> !products.containsKey(productId)).findFirst().ifPresent(productId -> {
            throw new ResourceNotFoundException("Không tìm thấy sản phẩm id=" + productId);
        });
        return products;
    }

    /**
     * BR-SAL-15: kho, khách, SP phải đang hoạt động — cả khi lưu nháp lẫn khi hoàn tất.
     * warehouse = null: đơn Chờ xác nhận chưa có kho (BR-SAL-17).
     */
    private static void checkUsable(Warehouse warehouse, Customer customer, Collection<Product> products) {
        if (warehouse != null && !warehouse.isActive()) {
            throw new BusinessException("Kho '" + warehouse.getName() + "' (" + warehouse.getCode()
                    + ") đang ngừng hoạt động, không thể xuất kho");
        }
        if (!customer.isActive()) {
            throw new BusinessException("Khách hàng '" + customer.getName() + "' (" + customer.getCode()
                    + ") đang ngừng hoạt động, không thể bán hàng");
        }
        List<Product> inactive = products.stream().filter(product -> !product.isActive()).toList();
        if (!inactive.isEmpty()) {
            throw new BusinessException("Sản phẩm đã ngừng kinh doanh, không thể bán: " + inactive.stream()
                    .map(product -> "'" + product.getName() + "' (" + product.getCode() + ")")
                    .collect(Collectors.joining(", ")));
        }
    }

    /** Ghi header và dòng hàng — cập nhật dòng TẠI CHỖ theo productId (lý do: xem InboundReceiptService.apply). */
    private static void apply(StockDocument document, SalesOrderRequest request, References refs) {
        document.setWarehouse(refs.warehouse());
        document.setCustomer(refs.customer());
        document.setDocumentDate(refs.documentDate());
        document.setNote(blankToNull(request.note()));

        Set<Long> requestedIds = refs.products().keySet();
        document.removeItemsIf(item -> !requestedIds.contains(item.getProduct().getId()));
        Map<Long, StockDocumentItem> existing = document.getItems().stream()
                .collect(Collectors.toMap(item -> item.getProduct().getId(), Function.identity()));

        for (SalesOrderLineRequest line : request.lines()) {
            StockDocumentItem item = existing.get(line.productId());
            if (item == null) {
                item = new StockDocumentItem(refs.products().get(line.productId()));
                document.addItem(item);
            }
            item.setValues(null, line.quantity(), line.unitPrice());
        }
        document.recalculateTotal();
    }

    // Đơn app: khách đã cố định (lấy từ token), kênh luôn APP; KHÔNG đụng note (ghi chú nội bộ của cửa hàng)
    private static void applyAppHeader(StockDocument document, Customer customer, LocalDate deliveryDate,
                                       String customerNote) {
        document.setCustomer(customer);
        document.setOrderChannel(OrderChannel.APP);
        document.setRequestedDeliveryDate(deliveryDate);
        document.setCustomerNote(blankToNull(customerNote));
    }

    private static void applyHeader(StockDocument document, PendingOrderRequest request, Customer customer) {
        document.setCustomer(customer);
        document.setOrderChannel(request.orderChannel());
        document.setRequestedDeliveryDate(request.requestedDeliveryDate());
        document.setNote(blankToNull(request.note()));
    }

    /**
     * Ghi phần khách đặt TẠI CHỖ theo productId (cùng lý do với apply): SP còn giữ → sửa SL, SP bỏ → xóa,
     * SP mới → thêm. Không xóa-hết-thêm-lại vì Hibernate có thể INSERT trước DELETE → vi phạm UNIQUE.
     */
    private void replaceRequestLines(StockDocument document, List<SaleRequestLineRequest> lines,
                                     Map<Long, Product> products) {
        Map<Long, SaleRequestItem> existing = saleRequestItemRepository.findByDocumentId(document.getId()).stream()
                .collect(Collectors.toMap(item -> item.getProduct().getId(), Function.identity()));
        saleRequestItemRepository.deleteAll(existing.values().stream()
                .filter(item -> !products.containsKey(item.getProduct().getId())).toList());

        List<SaleRequestItem> kept = new ArrayList<>();
        for (SaleRequestLineRequest line : lines) {
            SaleRequestItem item = existing.get(line.productId());
            if (item == null) {
                item = new SaleRequestItem(document, products.get(line.productId()), line.quantity());
            } else {
                item.setQuantity(line.quantity());
            }
            kept.add(item);
        }
        saleRequestItemRepository.saveAll(kept);
    }

    private SalesOrderResponse toResponse(StockDocument document) {
        if (document.getId() == null) {
            return SalesOrderResponse.from(document, List.of(), List.of(), List.of());
        }
        return SalesOrderResponse.from(document, saleRequestItemRepository.findByDocumentId(document.getId()),
                salePaymentRepository.findByDocumentId(document.getId()),
                salePriceAdjustmentRepository.findByDocumentId(document.getId()));
    }

    private String nextCode() {
        return CODE_FORMAT.formatted(stockDocumentRepository.nextOutboundCodeNumber());
    }

    private static LocalDate today() {
        return LocalDate.now(CommonSpecifications.BUSINESS_ZONE);
    }

    // 10000000 → "10.000.000 đ" (tiền thu và tổng phiếu luôn là số đồng nguyên)
    private static String money(BigDecimal amount) {
        return String.format(VIETNAMESE, "%,d đ", amount.toBigInteger());
    }

    private static String label(StockDocument document) {
        return "Phiếu bán " + document.getCode();
    }

    private static ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException("Không tìm thấy phiếu bán id=" + id);
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record References(Warehouse warehouse, Customer customer, Map<Long, Product> products,
                              LocalDate documentDate) {
    }

    private record RequestReferences(Customer customer, Map<Long, Product> products) {
    }
}
