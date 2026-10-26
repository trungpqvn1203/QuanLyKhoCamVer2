package com.grainwarehouse.cashbook.service;

import com.grainwarehouse.cashbook.dto.CashBookOpeningRequest;
import com.grainwarehouse.cashbook.dto.CashBookOpeningResponse;
import com.grainwarehouse.cashbook.dto.CashBookSummaryResponse;
import com.grainwarehouse.cashbook.dto.CashEntryResponse;
import com.grainwarehouse.cashbook.dto.CashLedgerResponse;
import com.grainwarehouse.cashbook.entity.CashAccount;
import com.grainwarehouse.cashbook.entity.CashBook;
import com.grainwarehouse.cashbook.entity.CashBookChange;
import com.grainwarehouse.cashbook.entity.CashEntrySource;
import com.grainwarehouse.cashbook.repository.CashBookChangeRepository;
import com.grainwarehouse.cashbook.repository.CashBookRepository;
import com.grainwarehouse.cashbook.repository.CashLedgerRepository;
import com.grainwarehouse.common.exception.BusinessException;
import com.grainwarehouse.common.exception.InvalidRequestException;
import com.grainwarehouse.common.search.CommonSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Sổ quỹ (BR-CASH-01..03, 06): đầu kỳ, số dư hiện tại, sổ quỹ theo khoảng ngày. Số liệu đọc từ VIEW cash_flow_entry —
 * không có bảng số dư lưu riêng: số dư = đầu kỳ + tổng các khoản có ngày từ đầu kỳ tới ngày cần xem.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CashBookService {

    /** Chặn trên khoảng xem sổ quỹ — một năm đủ cho mọi báo cáo thường ngày, giữ câu truy vấn nhẹ. */
    static final int MAX_RANGE_DAYS = 366;

    private static final DateTimeFormatter VN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CashBookRepository cashBookRepository;
    private final CashBookChangeRepository cashBookChangeRepository;
    private final CashLedgerRepository cashLedgerRepository;

    public CashBookOpeningResponse opening() {
        return CashBookOpeningResponse.of(cashBookRepository.findById(CashBook.ID).orElse(null),
                cashBookChangeRepository.findAllByOrderByIdDesc());
    }

    /**
     * BR-CASH-03: đặt hoặc sửa đầu kỳ. Ngày bắt đầu không ở tương lai (giờ VN). Mỗi lần đặt/sửa ghi một dòng lịch sử
     * với giá trị cũ — sửa đầu kỳ làm đổi mọi số dư nên phải còn dấu vết. Khóa dòng đầu kỳ trước khi chụp giá trị cũ.
     */
    @Transactional
    public CashBookOpeningResponse setOpening(CashBookOpeningRequest request, String changedBy) {
        if (request.startDate().isAfter(today())) {
            throw new InvalidRequestException("startDate", "Ngày bắt đầu sổ quỹ không được ở tương lai");
        }
        CashBook book = cashBookRepository.findForUpdate(CashBook.ID).orElse(null);
        cashBookChangeRepository.save(CashBookChange.of(book, request.startDate(), request.openingCash(),
                request.openingBank(), changedBy));
        if (book == null) {
            book = CashBook.create(request.startDate(), request.openingCash(), request.openingBank());
        } else {
            book.update(request.startDate(), request.openingCash(), request.openingBank());
        }
        cashBookRepository.saveAndFlush(book);
        return opening();
    }

    /** BR-CASH-06: số dư tới hôm nay của quỹ tiền mặt và tài khoản, thu/chi trong ngày, cờ âm để cảnh báo đỏ. */
    public CashBookSummaryResponse summary() {
        LocalDate today = today();
        CashBook book = cashBookRepository.findById(CashBook.ID).orElse(null);
        if (book == null) {
            return new CashBookSummaryResponse(false, today, null, null, null);
        }
        return new CashBookSummaryResponse(true, today, book.getStartDate(),
                accountSummary(book, CashAccount.CASH, today), accountSummary(book, CashAccount.BANK, today));
    }

    /**
     * BR-CASH-02: sổ quỹ của một tài khoản trong [from, to]. Mặc định: từ đầu tháng (giờ VN) tới hôm nay.
     * from sớm hơn ngày đầu kỳ thì kéo về ngày đầu kỳ (khoản trước đó không thuộc sổ quỹ).
     */
    public CashLedgerResponse ledger(CashAccount account, LocalDate from, LocalDate to, CashEntrySource source) {
        CashBook book = requireBook();
        LocalDate today = today();
        LocalDate end = to == null ? today : to;
        LocalDate start = from == null ? today.withDayOfMonth(1) : from;
        if (start.isBefore(book.getStartDate())) {
            start = book.getStartDate();
        }
        if (end.isBefore(book.getStartDate())) {
            throw new InvalidRequestException("to", "Đến ngày phải từ ngày đầu kỳ sổ quỹ ("
                    + book.getStartDate().format(VN_DATE) + ") trở đi");
        }
        if (end.isBefore(start)) {
            throw new InvalidRequestException("to", "Đến ngày phải sau hoặc bằng từ ngày");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_RANGE_DAYS) {
            throw new InvalidRequestException("from", "Xem tối đa " + MAX_RANGE_DAYS + " ngày một lần");
        }

        BigDecimal opening = balanceBefore(book, account, start);
        BigDecimal closing = opening.add(cashLedgerRepository.sum(account, start, end));
        List<CashLedgerRepository.Row> rows = cashLedgerRepository.entries(account, start, end, source);
        BigDecimal totalIn = rows.stream().map(CashLedgerRepository.Row::amount).filter(a -> a.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalOut = rows.stream().map(CashLedgerRepository.Row::amount).filter(a -> a.signum() < 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add).negate();
        return new CashLedgerResponse(account, book.getStartDate(), start, end, source, opening, totalIn, totalOut,
                closing, closing.signum() < 0,
                rows.stream().map(row -> CashEntryResponse.from(row, opening)).toList());
    }

    /** Cho CashVoucherService: phiếu chỉ lập được khi đã đặt đầu kỳ. */
    CashBook requireBook() {
        return cashBookRepository.findById(CashBook.ID).orElseThrow(() -> new BusinessException(
                "Chưa đặt đầu kỳ sổ quỹ. Hãy nhập ngày bắt đầu và số dư tiền mặt, tài khoản trước"));
    }

    private CashBookSummaryResponse.AccountSummary accountSummary(CashBook book, CashAccount account, LocalDate today) {
        BigDecimal balance = book.opening(account).add(cashLedgerRepository.sum(account, book.getStartDate(), today));
        CashLedgerRepository.InOut todayInOut = cashLedgerRepository.inOut(account, today, today);
        return new CashBookSummaryResponse.AccountSummary(account, balance, todayInOut.totalIn(),
                todayInOut.totalOut(), balance.signum() < 0);
    }

    /** Số dư ngay trước ngày `date` = đầu kỳ + các khoản từ đầu kỳ tới hết ngày hôm trước. */
    private BigDecimal balanceBefore(CashBook book, CashAccount account, LocalDate date) {
        if (!date.isAfter(book.getStartDate())) {
            return book.opening(account);
        }
        return book.opening(account).add(cashLedgerRepository.sum(account, book.getStartDate(), date.minusDays(1)));
    }

    static LocalDate today() {
        return LocalDate.now(CommonSpecifications.BUSINESS_ZONE);
    }
}
