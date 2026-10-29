package com.grainwarehouse.report.service;

import com.grainwarehouse.common.exception.InvalidRequestException;
import com.grainwarehouse.common.search.CommonSpecifications;
import com.grainwarehouse.inventory.dto.StockFigures;
import com.grainwarehouse.report.dto.DashboardResponse;
import com.grainwarehouse.report.dto.DashboardResponse.DailySales;
import com.grainwarehouse.report.dto.DashboardResponse.SalesPeriod;
import com.grainwarehouse.report.dto.StockByWarehouseResponse;
import com.grainwarehouse.report.dto.StockMovementResponse;
import com.grainwarehouse.report.repository.ReportRepository;
import com.grainwarehouse.report.repository.ReportRepository.DailyRow;
import com.grainwarehouse.report.repository.ReportRepository.ProductInfo;
import com.grainwarehouse.report.repository.ReportRepository.SalesTotals;
import com.grainwarehouse.report.repository.ReportRepository.StockCell;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Báo cáo tồn kho và trang tổng quan (BR-RPT-01..06). Chỉ đọc. */
@Service
@RequiredArgsConstructor
// READ COMMITTED mặc định: mỗi câu SQL thấy dữ liệu đã commit lúc nó chạy. Phiếu ghi xen giữa hai câu có thể làm
// các thẻ lệch nhau một phiếu — chấp nhận cho trang xem nhanh; báo cáo cần khớp tuyệt đối mới cần REPEATABLE READ
@Transactional(readOnly = true)
public class ReportService {

    static final int CHART_DAYS = 30;

    private final ReportRepository reportRepository;

    public DashboardResponse dashboard() {
        // Cùng cách lấy "hôm nay" với SalesOrderService → ngày trên tổng quan khớp ngày bán ghi trên phiếu
        LocalDate today = LocalDate.now(CommonSpecifications.BUSINESS_ZONE);
        ReportRepository.StockCounts counts = reportRepository.stockCounts();
        ReportRepository.DraftInbound inbound = reportRepository.draftInbound();
        DashboardResponse.StockSummary stock = new DashboardResponse.StockSummary(counts.productCount(),
                counts.outOfStockCount(), counts.lowStockCount(), counts.totalTons(), inbound.incomingTons(),
                inbound.count());

        LocalDate chartFrom = today.minusDays(CHART_DAYS - 1L);
        DashboardResponse.SalesSummary sales = new DashboardResponse.SalesSummary(
                period(reportRepository.salesTotals(today, today)),
                period(reportRepository.salesTotals(today.withDayOfMonth(1), today)),
                reportRepository.draftSalesCount(),
                fillDays(reportRepository.dailySales(chartFrom, today), chartFrom, today));
        return new DashboardResponse(today, stock, sales);
    }

    /** BR-RPT-04: bảng SP × kho. hideZero = chỉ SP còn hàng; SP đã ngừng kinh doanh luôn chỉ hiện khi còn hàng. */
    public StockByWarehouseResponse stockByWarehouse(Long categoryId, String keyword, boolean hideZero) {
        List<StockByWarehouseResponse.WarehouseColumn> warehouses = reportRepository.reportWarehouses().stream()
                .map(w -> new StockByWarehouseResponse.WarehouseColumn(w.id(), w.code(), w.name(), w.active()))
                .toList();

        // Gom các ô (SP, kho) thành từng dòng SP, giữ thứ tự mã SP của SQL
        Map<Long, List<StockCell>> byProduct = reportRepository.stockCells(categoryId, keyword).stream()
                .collect(Collectors.groupingBy(cell -> cell.product().id(), LinkedHashMap::new, Collectors.toList()));

        List<StockByWarehouseResponse.Row> rows = new ArrayList<>();
        Map<Long, BigDecimal> warehouseTons = new TreeMap<>();
        BigDecimal totalTons = BigDecimal.ZERO;
        for (List<StockCell> cells : byProduct.values()) {
            ProductInfo product = cells.getFirst().product();
            Map<Long, Long> quantities = cells.stream()
                    .filter(cell -> cell.warehouseId() != null)
                    .collect(Collectors.toMap(StockCell::warehouseId, StockCell::quantity, Long::sum, TreeMap::new));
            long total = quantities.values().stream().mapToLong(Long::longValue).sum();
            if (total == 0 && (hideZero || !product.active())) {
                continue;
            }
            BigDecimal tons = StockFigures.tons(total, product.netWeightKg());
            quantities.forEach((warehouseId, quantity) -> warehouseTons.merge(warehouseId,
                    StockFigures.tons(quantity, product.netWeightKg()), BigDecimal::add));
            totalTons = totalTons.add(tons);
            rows.add(new StockByWarehouseResponse.Row(product.id(), product.code(), product.name(),
                    product.categoryName(), product.unitName(), product.active(), quantities, total, tons));
        }
        return new StockByWarehouseResponse(warehouses, rows, warehouseTons, totalTons);
    }

    /**
     * BR-RPT-05: nhập – xuất – tồn trong [from, to] theo ngày giờ Việt Nam. SP không có số nào khác 0 thì bỏ.
     * Đến hôm nay thì tồn cuối = tồn hiện tại (cùng một sổ stock_transaction).
     */
    public StockMovementResponse stockMovement(LocalDate from, LocalDate to, Long warehouseId, Long categoryId) {
        if (from == null) {
            throw new InvalidRequestException("from", "Chọn ngày bắt đầu");
        }
        if (to == null) {
            throw new InvalidRequestException("to", "Chọn ngày kết thúc");
        }
        if (to.isBefore(from)) {
            throw new InvalidRequestException("to", "Ngày kết thúc phải từ ngày bắt đầu trở đi");
        }
        Instant start = from.atStartOfDay(CommonSpecifications.BUSINESS_ZONE).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(CommonSpecifications.BUSINESS_ZONE).toInstant();

        List<StockMovementResponse.Row> rows = new ArrayList<>();
        BigDecimal[] totals = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        for (ReportRepository.MovementRow row : reportRepository.movement(start, end, warehouseId, categoryId)) {
            long closing = row.opening() + row.inbound() - row.outbound();
            if (row.opening() == 0 && row.inbound() == 0 && row.outbound() == 0 && closing == 0) {
                continue;
            }
            ProductInfo product = row.product();
            Function<Long, BigDecimal> tons = quantity -> StockFigures.tons(quantity, product.netWeightKg());
            StockMovementResponse.Row result = new StockMovementResponse.Row(product.id(), product.code(),
                    product.name(), product.categoryName(), product.unitName(), row.opening(), row.inbound(),
                    row.outbound(), closing, tons.apply(row.opening()), tons.apply(row.inbound()),
                    tons.apply(row.outbound()), tons.apply(closing));
            totals[0] = totals[0].add(result.openingTons());
            totals[1] = totals[1].add(result.inboundTons());
            totals[2] = totals[2].add(result.outboundTons());
            totals[3] = totals[3].add(result.closingTons());
            rows.add(result);
        }
        return new StockMovementResponse(from, to, warehouseId, rows,
                new StockMovementResponse.Totals(totals[0], totals[1], totals[2], totals[3]));
    }

    private static SalesPeriod period(SalesTotals totals) {
        return new SalesPeriod(totals.revenue(), totals.orderCount(), totals.tons());
    }

    /** Điền 0 cho ngày không bán để biểu đồ luôn đủ 30 cột, đúng thứ tự ngày. */
    private static List<DailySales> fillDays(List<DailyRow> rows, LocalDate from, LocalDate to) {
        Map<LocalDate, DailyRow> byDate = rows.stream().collect(Collectors.toMap(DailyRow::date, Function.identity()));
        List<DailySales> days = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            DailyRow row = byDate.get(date);
            days.add(row == null
                    ? new DailySales(date, BigDecimal.ZERO, 0)
                    : new DailySales(date, row.revenue(), row.orderCount()));
        }
        return days;
    }
}
