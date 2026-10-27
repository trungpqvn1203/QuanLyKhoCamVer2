package com.grainwarehouse.opening.service;

import com.grainwarehouse.common.exception.BusinessException;
import com.grainwarehouse.common.exception.InvalidRequestException;
import com.grainwarehouse.common.exception.ResourceNotFoundException;
import com.grainwarehouse.common.response.PageResponse;
import com.grainwarehouse.common.search.CommonSpecifications;
import com.grainwarehouse.customer.entity.Customer;
import com.grainwarehouse.customer.repository.CustomerRepository;
import com.grainwarehouse.inbound.dto.SupplierPaymentResponse;
import com.grainwarehouse.inbound.entity.SupplierPayment;
import com.grainwarehouse.inbound.entity.SupplierPaymentType;
import com.grainwarehouse.inbound.repository.SupplierPaymentRepository;
import com.grainwarehouse.inventory.entity.StockDocument;
import com.grainwarehouse.inventory.entity.StockDocumentStatus;
import com.grainwarehouse.inventory.repository.StockDocumentRepository;
import com.grainwarehouse.opening.dto.OpeningDebtLineRequest;
import com.grainwarehouse.opening.dto.OpeningDebtParty;
import com.grainwarehouse.opening.dto.OpeningDebtRequest;
import com.grainwarehouse.opening.dto.OpeningDebtResponse;
import com.grainwarehouse.opening.dto.OpeningDebtSearchCriteria;
import com.grainwarehouse.opening.repository.OpeningDebtSpecifications;
import com.grainwarehouse.sales.dto.RecordPaymentRequest;
import com.grainwarehouse.sales.dto.SalePaymentResponse;
import com.grainwarehouse.sales.entity.PaymentMethod;
import com.grainwarehouse.sales.entity.SalePayment;
import com.grainwarehouse.sales.entity.SalePaymentType;
import com.grainwarehouse.sales.repository.SalePaymentRepository;
import com.grainwarehouse.supplier.entity.Supplier;
import com.grainwarehouse.supplier.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.grainwarehouse.inventory.entity.StockDocumentStatus.REVERSED;
import static com.grainwarehouse.opening.dto.OpeningDebtParty.CUSTOMER;

/**
 * Công nợ đầu kỳ khách hàng / NCC (BR-ODB-01..09): khoản nợ cũ từ trước khi dùng phần mềm.
 *
 * <p>Mỗi khoản là một chứng từ không có kho, không có dòng hàng, tạo thẳng ở trạng thái Hoàn tất (chỉ một con số
 * nên không cần nháp). Thu / trả và hoàn khoản thu nhầm dùng lại bảng sale_payment / supplier_payment — vì vậy tiền
 * thu nợ cũ tự vào sổ quỹ, và trang công nợ tự cộng khoản này. Logic thu/trả giống hệt phiếu bán / phiếu nhập
 * (BR-SAL-11..13, BR-SPAY-02..03), kể cả khóa chứng từ FOR UPDATE trước khi kiểm tra.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OpeningDebtService {

    // Dấu chấm ngăn cách hàng nghìn: 10.000.000 đ
    private static final Locale VIETNAMESE = Locale.forLanguageTag("vi-VN");

    private final StockDocumentRepository stockDocumentRepository;
    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;
    private final SalePaymentRepository salePaymentRepository;
    private final SupplierPaymentRepository supplierPaymentRepository;

    public PageResponse<OpeningDebtResponse> list(OpeningDebtParty party, OpeningDebtSearchCriteria criteria,
                                                  Pageable pageable) {
        return PageResponse.from(
                stockDocumentRepository.findAll(OpeningDebtSpecifications.from(criteria.withParty(party)), pageable),
                document -> OpeningDebtResponse.from(document, party, null));
    }

    public OpeningDebtResponse get(OpeningDebtParty party, Long id) {
        return toResponse(stockDocumentRepository.findWithDetailsByIdAndType(id, party.documentType())
                .orElseThrow(() -> notFound(id)), party);
    }

    /**
     * BR-ODB-04: ghi nhận theo lô — mỗi dòng một chứng từ Hoàn tất, cả lô một transaction (một dòng lỗi → không ghi
     * dòng nào). Thứ tự lỗi: 400 (ngày, trùng trong lô) → 404 (không có khách/NCC) → 409 (ngừng hoạt động, đã có nợ
     * đầu kỳ). Hai người cùng ghi cho một khách cùng lúc: UNIQUE uk_stock_document_opening_*_debt chặn người sau.
     */
    @Transactional
    public List<OpeningDebtResponse> create(OpeningDebtParty party, OpeningDebtRequest request, String username) {
        LocalDate documentDate = resolveDocumentDate(request.documentDate());
        List<Long> partyIds = distinctPartyIds(request.lines());
        Map<Long, Party> parties = loadParties(party, partyIds);

        List<Party> inactive = partyIds.stream().map(parties::get).filter(p -> !p.active()).toList();
        if (!inactive.isEmpty()) {
            throw new BusinessException(capitalize(party.label()) + " đang ngừng hoạt động, không nhập nợ đầu kỳ: "
                    + labels(inactive));
        }
        Set<Long> existing = new HashSet<>(party == CUSTOMER
                ? stockDocumentRepository.findCustomersWithOpeningDebt(partyIds)
                : stockDocumentRepository.findSuppliersWithOpeningDebt(partyIds));
        List<Party> duplicated = partyIds.stream().filter(existing::contains).map(parties::get).toList();
        if (!duplicated.isEmpty()) {
            throw new BusinessException("Đã có nợ đầu kỳ cho " + party.label() + ": " + labels(duplicated)
                    + ". Mỗi " + party.label() + " chỉ một khoản; nhập sai thì đảo khoản cũ rồi nhập lại");
        }

        Instant now = Instant.now();
        List<StockDocument> documents = new ArrayList<>();
        for (OpeningDebtLineRequest line : request.lines()) {
            StockDocument document = new StockDocument(party.documentType(), nextCode(party), username);
            Party target = parties.get(line.partyId());
            if (party == CUSTOMER) {
                document.setCustomer(target.customer());
            } else {
                document.setSupplier(target.supplier());
            }
            document.setDocumentDate(documentDate);
            document.setNote(blankToNull(line.note()));
            document.setOpeningDebtAmount(line.amount());
            document.markCompleted(now, username);
            documents.add(document);
        }
        // Flush ngay: vi phạm UNIQUE (ghi đồng thời) nổ ra ở đây thành DataIntegrityViolationException → 409
        return stockDocumentRepository.saveAllAndFlush(documents).stream()
                .map(document -> OpeningDebtResponse.from(document, party, List.of()))
                .toList();
    }

    /**
     * BR-ODB-06: nhập sai → đảo (lý do bắt buộc), chỉ khi CHƯA thu/trả đồng nào — giống BR-SAL-14 / BR-SPAY-04.
     * Đảo xong được nhập lại nợ đầu kỳ cho khách / NCC đó.
     */
    @Transactional
    public OpeningDebtResponse reverse(OpeningDebtParty party, Long id, String reason) {
        StockDocument document = lockForChange(party, id, "đảo");
        if (document.getPaidAmount().signum() > 0) {
            throw new BusinessException(label(document) + " đã " + paidVerb(party) + " " + money(document.getPaidAmount())
                    + ". Hãy hoàn các khoản đó trước khi đảo");
        }
        document.markReversed(reason.trim(), Instant.now());
        return toResponse(stockDocumentRepository.saveAndFlush(document), party);
    }

    /** BR-ODB-07: thu (khách) / trả (NCC) như phiếu bán / phiếu nhập — tiền mặt và/hoặc chuyển khoản, không vượt số còn nợ. */
    @Transactional
    public OpeningDebtResponse recordPayment(OpeningDebtParty party, Long id, RecordPaymentRequest request) {
        BigDecimal cash = zeroIfNull(request.cashAmount());
        BigDecimal transfer = zeroIfNull(request.transferAmount());
        BigDecimal total = cash.add(transfer);
        if (total.signum() == 0) {
            throw new InvalidRequestException("cashAmount", "Phải nhập số tiền mặt hoặc chuyển khoản lớn hơn 0");
        }

        StockDocument document = lockForChange(party, id, paidVerb(party));
        if (total.compareTo(document.getOutstandingAmount()) > 0) {
            throw new BusinessException("Số tiền " + paidVerb(party) + " " + money(total) + " vượt số còn nợ "
                    + money(document.getOutstandingAmount()) + " của " + label(document).toLowerCase());
        }
        LocalDate paymentDate = request.paymentDate() == null ? today() : request.paymentDate();
        String note = blankToNull(request.note());

        if (party == CUSTOMER) {
            List<SalePayment> payments = new ArrayList<>();
            if (cash.signum() > 0) {
                payments.add(SalePayment.payment(document, PaymentMethod.CASH, cash, paymentDate, note));
            }
            if (transfer.signum() > 0) {
                payments.add(SalePayment.payment(document, PaymentMethod.BANK_TRANSFER, transfer, paymentDate, note));
            }
            salePaymentRepository.saveAll(payments);
        } else {
            List<SupplierPayment> payments = new ArrayList<>();
            if (cash.signum() > 0) {
                payments.add(SupplierPayment.payment(document, PaymentMethod.CASH, cash, paymentDate, note));
            }
            if (transfer.signum() > 0) {
                payments.add(SupplierPayment.payment(document, PaymentMethod.BANK_TRANSFER, transfer, paymentDate, note));
            }
            supplierPaymentRepository.saveAll(payments);
        }
        document.addPaidAmount(total);
        return toResponse(stockDocumentRepository.saveAndFlush(document), party);
    }

    /** BR-ODB-07: hoàn toàn bộ một khoản thu/trả ghi nhầm (lý do bắt buộc, ngày = hôm nay), mỗi khoản một lần. */
    @Transactional
    public OpeningDebtResponse reversePayment(OpeningDebtParty party, Long id, Long paymentId, String reason) {
        StockDocument document = lockForChange(party, id, "hoàn khoản " + paidVerb(party));
        BigDecimal delta;
        if (party == CUSTOMER) {
            SalePayment original = salePaymentRepository.findByIdAndDocumentId(paymentId, id)
                    .orElseThrow(() -> paymentNotFound(paymentId, document));
            checkReversible(original.getType() == SalePaymentType.PAYMENT,
                    salePaymentRepository.existsByReversalOfId(original.getId()));
            delta = salePaymentRepository.save(SalePayment.reversalOf(original, today(), reason.trim())).signedAmount();
        } else {
            SupplierPayment original = supplierPaymentRepository.findByIdAndDocumentId(paymentId, id)
                    .orElseThrow(() -> paymentNotFound(paymentId, document));
            checkReversible(original.getType() == SupplierPaymentType.PAYMENT,
                    supplierPaymentRepository.existsByReversalOfId(original.getId()));
            delta = supplierPaymentRepository.save(SupplierPayment.reversalOf(original, today(), reason.trim()))
                    .signedAmount();
        }
        document.addPaidAmount(delta);
        return toResponse(stockDocumentRepository.saveAndFlush(document), party);
    }

    /** Khóa trước, kiểm tra trạng thái sau (BR-IMP-14). Chỉ khoản Hoàn tất mới thu/trả/đảo được. */
    private StockDocument lockForChange(OpeningDebtParty party, Long id, String action) {
        StockDocument document = stockDocumentRepository.findForUpdate(id, party.documentType())
                .orElseThrow(() -> notFound(id));
        if (document.getStatus() == REVERSED) {
            throw new BusinessException(label(document) + " đã được đảo, không " + action + " được nữa");
        }
        if (document.getStatus() != StockDocumentStatus.COMPLETED) {
            throw new BusinessException(label(document) + " đang ở trạng thái " + document.getStatus().label());
        }
        return document;
    }

    private static void checkReversible(boolean isPayment, boolean alreadyReversed) {
        if (!isPayment) {
            throw new BusinessException("Chỉ hoàn được khoản thu/trả, không hoàn một dòng hoàn");
        }
        if (alreadyReversed) {
            throw new BusinessException("Khoản này đã được hoàn. Mỗi khoản chỉ được hoàn một lần");
        }
    }

    private Map<Long, Party> loadParties(OpeningDebtParty party, List<Long> ids) {
        Map<Long, Party> parties = party == CUSTOMER
                ? customerRepository.findAllById(ids).stream().map(Party::of)
                        .collect(Collectors.toMap(Party::id, Function.identity()))
                : supplierRepository.findAllById(ids).stream().map(Party::of)
                        .collect(Collectors.toMap(Party::id, Function.identity()));
        ids.stream().filter(id -> !parties.containsKey(id)).findFirst().ifPresent(id -> {
            throw new ResourceNotFoundException("Không tìm thấy " + party.label() + " id=" + id);
        });
        return parties;
    }

    // BR-ODB-04: mỗi khách / NCC một dòng trong lô
    private static List<Long> distinctPartyIds(List<OpeningDebtLineRequest> lines) {
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < lines.size(); i++) {
            if (!seen.add(lines.get(i).partyId())) {
                throw new InvalidRequestException("lines[" + i + "].partyId", "Đã có ở một dòng khác");
            }
        }
        return lines.stream().map(OpeningDebtLineRequest::partyId).toList();
    }

    // BR-ODB-02: ngày chốt nợ mặc định hôm nay theo giờ Việt Nam, lùi được, không ở tương lai
    private static LocalDate resolveDocumentDate(LocalDate requested) {
        if (requested == null) {
            return today();
        }
        if (requested.isAfter(today())) {
            throw new InvalidRequestException("documentDate", "Ngày chốt nợ không được ở tương lai");
        }
        return requested;
    }

    private String nextCode(OpeningDebtParty party) {
        long number = party == CUSTOMER
                ? stockDocumentRepository.nextOpeningCustomerDebtCodeNumber()
                : stockDocumentRepository.nextOpeningSupplierDebtCodeNumber();
        return party.codeFormat().formatted(number);
    }

    private OpeningDebtResponse toResponse(StockDocument document, OpeningDebtParty party) {
        if (party == CUSTOMER) {
            List<SalePayment> payments = salePaymentRepository.findByDocumentId(document.getId());
            Set<Long> reversedIds = payments.stream().filter(p -> p.getReversalOf() != null)
                    .map(p -> p.getReversalOf().getId()).collect(Collectors.toSet());
            return OpeningDebtResponse.from(document, party,
                    payments.stream().map(p -> SalePaymentResponse.from(p, reversedIds)).toList());
        }
        List<SupplierPayment> payments = supplierPaymentRepository.findByDocumentId(document.getId());
        Set<Long> reversedIds = payments.stream().filter(p -> p.getReversalOf() != null)
                .map(p -> p.getReversalOf().getId()).collect(Collectors.toSet());
        return OpeningDebtResponse.from(document, party,
                payments.stream().map(p -> SupplierPaymentResponse.from(p, reversedIds)).toList());
    }

    private static String paidVerb(OpeningDebtParty party) {
        return party == CUSTOMER ? "thu" : "trả";
    }

    private static String label(StockDocument document) {
        return "Nợ đầu kỳ " + document.getCode();
    }

    private static String labels(List<Party> parties) {
        return parties.stream().map(p -> "'" + p.name() + "' (" + p.code() + ")").collect(Collectors.joining(", "));
    }

    private static String capitalize(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException("Không tìm thấy khoản nợ đầu kỳ id=" + id);
    }

    private static ResourceNotFoundException paymentNotFound(Long paymentId, StockDocument document) {
        return new ResourceNotFoundException("Không tìm thấy khoản id=" + paymentId + " của "
                + label(document).toLowerCase());
    }

    private static LocalDate today() {
        return LocalDate.now(CommonSpecifications.BUSINESS_ZONE);
    }

    private static String money(BigDecimal amount) {
        return String.format(VIETNAMESE, "%,d đ", amount.toBigInteger());
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Khách hoặc NCC — phần chung đủ để kiểm tra và báo lỗi. */
    private record Party(Long id, String code, String name, boolean active, Customer customer, Supplier supplier) {

        static Party of(Customer customer) {
            return new Party(customer.getId(), customer.getCode(), customer.getName(), customer.isActive(), customer, null);
        }

        static Party of(Supplier supplier) {
            return new Party(supplier.getId(), supplier.getCode(), supplier.getName(), supplier.isActive(), null, supplier);
        }
    }
}
