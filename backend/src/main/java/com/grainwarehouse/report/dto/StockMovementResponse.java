package com.grainwarehouse.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Báo cáo nhập – xuất – tồn theo kỳ (BR-RPT-05): tồn cuối = tồn đầu + nhập − xuất, theo ngày ghi sổ.
 * warehouseId không có = mọi kho.
 */
public record StockMovementResponse(
        LocalDate from,
        LocalDate to,
        Long warehouseId,
        List<Row> rows,
        Totals totals) {

    public record Row(
            Long productId,
            String productCode,
            String productName,
            String categoryName,
            String unitName,
            long openingQuantity,
            long inboundQuantity,
            long outboundQuantity,
            long closingQuantity,
            BigDecimal openingTons,
            BigDecimal inboundTons,
            BigDecimal outboundTons,
            BigDecimal closingTons) {
    }

    /** Chỉ cộng tấn: cộng số lượng khác đơn vị là vô nghĩa. */
    public record Totals(BigDecimal openingTons, BigDecimal inboundTons, BigDecimal outboundTons,
                         BigDecimal closingTons) {
    }
}
