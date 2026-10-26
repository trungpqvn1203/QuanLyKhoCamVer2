package com.grainwarehouse.cashbook.service;

import com.grainwarehouse.cashbook.dto.CashVoucherRequest;
import com.grainwarehouse.cashbook.dto.CashVoucherResponse;
import com.grainwarehouse.cashbook.dto.CashVoucherSearchCriteria;
import com.grainwarehouse.cashbook.entity.CashBook;
import com.grainwarehouse.cashbook.entity.CashCategory;
import com.grainwarehouse.cashbook.entity.CashDirection;
import com.grainwarehouse.cashbook.entity.CashVoucher;
import com.grainwarehouse.cashbook.entity.CashVoucherType;
import com.grainwarehouse.cashbook.repository.CashCategoryRepository;
import com.grainwarehouse.cashbook.repository.CashVoucherRepository;
import com.grainwarehouse.cashbook.repository.CashVoucherSpecifications;
import com.grainwarehouse.common.exception.BusinessException;
import com.grainwarehouse.common.exception.InvalidRequestException;
import com.grainwarehouse.common.exception.ResourceNotFoundException;
import com.grainwarehouse.common.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

/**
 * Phiếu thu / phiếu chi khác (BR-CASH-04): tiền điện, xăng xe, thuê kho, chủ góp vốn / rút tiền... Bất biến; ghi nhầm
 * thì lập phiếu hoàn. Phiếu vào sổ quỹ qua VIEW cash_flow_entry, không có bước "ghi sổ" riêng.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CashVoucherService {

    private static final String RECEIPT_CODE = "PT%06d";
    private static final String PAYMENT_CODE = "PC%06d";
    private static final DateTimeFormatter VN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CashVoucherRepository cashVoucherRepository;
    private final CashCategoryRepository cashCategoryRepository;
    private final CashBookService cashBookService;

    /** Mới nhất lên đầu (sắp xếp ở controller). */
    public PageResponse<CashVoucherResponse> list(CashVoucherSearchCriteria criteria, Pageable pageable) {
        Page<CashVoucher> page = cashVoucherRepository.findAll(CashVoucherSpecifications.from(criteria), pageable);
        Set<Long> reversedIds = page.isEmpty() ? Set.of()
                : new HashSet<>(cashVoucherRepository.findReversedIds(page.map(CashVoucher::getId).getContent()));
        return PageResponse.from(page, voucher -> CashVoucherResponse.from(voucher, reversedIds.contains(voucher.getId())));
    }

    /**
     * BR-CASH-04, 05: lập phiếu. Thứ tự lỗi: 400 (dữ liệu) → 404 (danh mục) → 409 (nghiệp vụ). Danh mục phải đang dùng
     * và cùng chiều với phiếu; ngày phiếu không trước ngày đầu kỳ (phiếu trước đầu kỳ sẽ không vào sổ — dễ "mất tiền").
     */
    @Transactional
    public CashVoucherResponse create(CashVoucherRequest request) {
        CashBook book = cashBookService.requireBook();
        LocalDate voucherDate = request.voucherDate() == null ? CashBookService.today() : request.voucherDate();
        if (voucherDate.isBefore(book.getStartDate())) {
            throw new InvalidRequestException("voucherDate", "Ngày phiếu trước ngày đầu kỳ sổ quỹ ("
                    + book.getStartDate().format(VN_DATE) + ") — phiếu sẽ không vào sổ quỹ");
        }
        CashCategory category = cashCategoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục thu/chi id=" + request.categoryId()));
        if (category.getDirection() != request.direction()) {
            throw new InvalidRequestException("categoryId", "Danh mục '" + category.getName() + "' là danh mục "
                    + label(category.getDirection()) + ", không dùng cho phiếu " + label(request.direction()));
        }
        if (!category.isActive()) {
            throw new BusinessException("Danh mục '" + category.getName() + "' đã ngừng dùng");
        }

        String code = request.direction() == CashDirection.IN
                ? RECEIPT_CODE.formatted(cashVoucherRepository.nextReceiptCodeNumber())
                : PAYMENT_CODE.formatted(cashVoucherRepository.nextPaymentCodeNumber());
        CashVoucher voucher = CashVoucher.voucher(code, request.direction(), request.account(), category,
                request.amount(), voucherDate, blankToNull(request.counterparty()), blankToNull(request.note()));
        return CashVoucherResponse.from(cashVoucherRepository.saveAndFlush(voucher), false);
    }

    /**
     * BR-CASH-04: hoàn một phiếu ghi nhầm bằng phiếu hoàn (mã gốc + "-H") cùng chiều, tài khoản, danh mục, số tiền;
     * ngày = hôm nay; lý do bắt buộc. Mỗi phiếu hoàn một lần (UNIQUE reversal_of_id là tuyến phòng thủ cuối).
     */
    @Transactional
    public CashVoucherResponse reverse(Long id, String reason) {
        CashVoucher original = cashVoucherRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiếu thu/chi id=" + id));
        if (original.getType() != CashVoucherType.VOUCHER) {
            throw new BusinessException("Chỉ hoàn được phiếu thu/chi, không hoàn một phiếu hoàn");
        }
        if (cashVoucherRepository.existsByReversalOfId(original.getId())) {
            throw new BusinessException("Phiếu " + original.getCode() + " đã được hoàn. Mỗi phiếu chỉ được hoàn một lần");
        }
        CashVoucher reversal = CashVoucher.reversalOf(original, CashBookService.today(), reason.trim());
        return CashVoucherResponse.from(cashVoucherRepository.saveAndFlush(reversal), false);
    }

    private static String label(CashDirection direction) {
        return direction == CashDirection.IN ? "thu" : "chi";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
