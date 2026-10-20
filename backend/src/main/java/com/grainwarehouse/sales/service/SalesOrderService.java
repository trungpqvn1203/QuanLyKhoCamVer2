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
import com.grainwarehouse.inventory.repository.StockDocumentRepository;
import com.grainwarehouse.inventory.repository.StockTransactionRepository;
import com.grainwarehouse.inventory.service.InventoryService;
import com.grainwarehouse.product.entity.Product;
import com.grainwarehouse.product.repository.ProductRepository;
import com.grainwarehouse.sales.dto.RecordPaymentRequest;
import com.grainwarehouse.sales.dto.SalesOrderLineRequest;
import com.grainwarehouse.sales.dto.SalesOrderRequest;
import com.grainwarehouse.sales.dto.SalesOrderResponse;
import com.grainwarehouse.sales.dto.SalesOrderSearchCriteria;
import com.grainwarehouse.sales.dto.SalesOrderSummaryResponse;
import com.grainwarehouse.sales.entity.PaymentMethod;
import com.grainwarehouse.sales.entity.SalePayment;
import com.grainwarehouse.sales.entity.SalePaymentType;
import com.grainwarehouse.sales.repository.SalePaymentRepository;
import com.grainwarehouse.sales.repository.SalesOrderSpecifications;
import com.grainwarehouse.warehouse.entity.Warehouse;
import com.grainwarehouse.warehouse.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.grainwarehouse.inventory.entity.StockDocumentStatus.COMPLETED;
import static com.grainwarehouse.inventory.entity.StockDocumentStatus.DRAFT;
import static com.grainwarehouse.inventory.entity.StockDocumentStatus.REVERSED;

/**
 * Phiếu bán / xuất kho và thanh toán của phiếu (BR-SAL-01..15).
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

    private final StockDocumentRepository stockDocumentRepository;
    private final StockTransactionRepository stockTransactionRepository;
    private final SalePaymentRepository salePaymentRepository;
    private final WarehouseRepository warehouseRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;

    public PageResponse<SalesOrderSummaryResponse> list(SalesOrderSearchCriteria criteria, Pageable pageable) {
        return PageResponse.from(
                stockDocumentRepository.findAll(SalesOrderSpecifications.from(criteria), pageable),
                SalesOrderSummaryResponse::from);
    }

    public SalesOrderResponse get(Long id) {
        return toResponse(stockDocumentRepository.findWithDetailsByIdAndType(id, TYPE)
                .orElseThrow(() -> notFound(id)));
    }

    /** BR-SAL-02: tạo phiếu Nháp — chưa ảnh hưởng tồn, không giữ chỗ tồn. */
    @Transactional
    public SalesOrderResponse create(SalesOrderRequest request) {
        References refs = resolve(request);
        String code = CODE_FORMAT.formatted(stockDocumentRepository.nextOutboundCodeNumber());
        StockDocument document = new StockDocument(TYPE, code);
        apply(document, request, refs);
        return toResponse(stockDocumentRepository.save(document));
    }

    @Transactional
    public SalesOrderResponse update(Long id, SalesOrderRequest request) {
        StockDocument document = lockForChange(id, DRAFT, "sửa");
        References refs = resolve(request);
        apply(document, request, refs);
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

    @Transactional
    public SalesOrderResponse cancel(Long id, String reason) {
        StockDocument document = lockForChange(id, DRAFT, "hủy");
        document.markCancelled(blankToNull(reason), Instant.now());
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
        StockDocument document = stockDocumentRepository.findForUpdate(id, TYPE).orElseThrow(() -> notFound(id));
        if (document.getStatus() != required) {
            throw new BusinessException(statusViolation(document, required, action));
        }
        return document;
    }

    private static String statusViolation(StockDocument document, StockDocumentStatus required, String action) {
        if (required == COMPLETED && document.getStatus() == REVERSED && action.equals("đảo")) {
            return label(document) + " đã được đảo. Mỗi phiếu chỉ được đảo một lần";
        }
        return label(document) + " đang ở trạng thái " + document.getStatus().label()
                + ". Chỉ phiếu " + required.label() + " mới được " + action;
    }

    /** Đọc và kiểm tra mọi thứ request tham chiếu tới, TRƯỚC khi đổi phiếu. Thứ tự: 400 → 404 → 409. */
    private References resolve(SalesOrderRequest request) {
        LocalDate documentDate = resolveDocumentDate(request.documentDate());
        List<Long> productIds = distinctProductIds(request.lines());

        Warehouse warehouse = warehouseRepository.findById(request.warehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kho id=" + request.warehouseId()));
        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy khách hàng id=" + request.customerId()));
        Map<Long, Product> products = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        productIds.stream().filter(productId -> !products.containsKey(productId)).findFirst().ifPresent(productId -> {
            throw new ResourceNotFoundException("Không tìm thấy sản phẩm id=" + productId);
        });

        checkUsable(warehouse, customer, products.values());
        return new References(warehouse, customer, products, documentDate);
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
    private static List<Long> distinctProductIds(List<SalesOrderLineRequest> lines) {
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < lines.size(); i++) {
            if (!seen.add(lines.get(i).productId())) {
                throw new InvalidRequestException("lines[" + i + "].productId",
                        "Sản phẩm này đã có ở một dòng khác của phiếu");
            }
        }
        return lines.stream().map(SalesOrderLineRequest::productId).toList();
    }

    /** BR-SAL-15: kho, khách, SP phải đang hoạt động — cả khi lưu nháp lẫn khi hoàn tất. */
    private static void checkUsable(Warehouse warehouse, Customer customer, Collection<Product> products) {
        if (!warehouse.isActive()) {
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

    private SalesOrderResponse toResponse(StockDocument document) {
        List<SalePayment> payments = document.getId() == null
                ? List.of() : salePaymentRepository.findByDocumentId(document.getId());
        return SalesOrderResponse.from(document, payments);
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
}
