package com.grainwarehouse.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Trang tổng quan (BR-RPT-01..03): chỉ phần mọi vai trò đều được xem (tồn kho, bán hàng). Công nợ và sổ quỹ
 * frontend lấy từ API sẵn có của từng module, theo quyền của người xem.
 */
public record DashboardResponse(LocalDate today, StockSummary stock, SalesSummary sales) {

    /** Chỉ tính SP đang kinh doanh. Tình trạng theo tổng mọi kho (BR-STK-04). */
    public record StockSummary(
            long productCount,
            long outOfStockCount,
            long lowStockCount,
            BigDecimal totalTons,
            // Hàng đang về = SL đặt trên phiếu nhập nháp (BR-STK-06), quy ra tấn
            BigDecimal incomingTons,
            long draftInboundCount) {
    }

    public record SalesSummary(
            SalesPeriod today,
            SalesPeriod thisMonth,
            long draftCount,
            // Đủ 30 ngày gần nhất (kể cả hôm nay), ngày không bán = 0 → biểu đồ không bị hụt cột
            List<DailySales> last30Days) {
    }

    /** Doanh số = tổng tiền phiếu bán đã hoàn tất theo ngày bán; phiếu đã đảo không tính (BR-RPT-02). */
    public record SalesPeriod(BigDecimal revenue, long orderCount, BigDecimal tons) {
    }

    public record DailySales(LocalDate date, BigDecimal revenue, long orderCount) {
    }
}
