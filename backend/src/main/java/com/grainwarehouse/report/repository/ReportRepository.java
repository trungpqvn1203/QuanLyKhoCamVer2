package com.grainwarehouse.report.repository;

import com.grainwarehouse.common.search.CommonSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Truy vấn tổng hợp cho báo cáo (BR-RPT) bằng SQL thuần: SUM / COUNT FILTER / GROUP BY trên nhiều bảng đọc rõ hơn
 * JPQL và không phải nạp từng entity lên rồi cộng trong Java. Chỉ đọc — không có entity riêng.
 */
@Repository
@RequiredArgsConstructor
public class ReportRepository {

    /** Phiếu bán tính doanh số: đã hoàn tất. Phiếu đã đảo (khách trả hàng / bán nhầm) không tính. */
    private static final String COMPLETED_SALE = "d.type = 'OUTBOUND' AND d.status = 'COMPLETED'";

    private final NamedParameterJdbcTemplate jdbc;

    public record StockCounts(long productCount, long outOfStockCount, long lowStockCount, BigDecimal totalTons) {
    }

    /**
     * Đếm SP đang kinh doanh theo tình trạng tồn trên tổng mọi kho (BR-STK-04); tổng tấn tính MỌI SP và mọi kho,
     * kể cả SP đã ngừng / kho đã ngừng mà còn hàng (BR-STK-01).
     */
    public StockCounts stockCounts() {
        return jdbc.queryForObject("""
                WITH totals AS (
                    SELECT p.active, p.net_weight_kg, p.min_stock_quantity, COALESCE(SUM(i.quantity), 0) AS total
                    FROM product p
                    LEFT JOIN inventory i ON i.product_id = p.id
                    GROUP BY p.id
                )
                SELECT count(*) FILTER (WHERE active)                AS product_count,
                       count(*) FILTER (WHERE active AND total = 0)  AS out_of_stock,
                       count(*) FILTER (WHERE active AND total > 0 AND min_stock_quantity IS NOT NULL
                                        AND total <= min_stock_quantity) AS low_stock,
                       COALESCE(ROUND(SUM(total * net_weight_kg) / 1000, 3), 0) AS total_tons
                FROM totals
                """, new MapSqlParameterSource(), (rs, n) -> new StockCounts(rs.getLong("product_count"),
                rs.getLong("out_of_stock"), rs.getLong("low_stock"), rs.getBigDecimal("total_tons")));
    }

    public record DraftInbound(long count, BigDecimal incomingTons) {
    }

    /** Phiếu nhập nháp và hàng đang về (SL đặt, BR-STK-06) quy ra tấn. */
    public DraftInbound draftInbound() {
        return jdbc.queryForObject("""
                SELECT (SELECT count(*) FROM stock_document WHERE type = 'INBOUND' AND status = 'DRAFT') AS drafts,
                       COALESCE(ROUND(SUM(COALESCE(i.ordered_quantity, i.quantity) * p.net_weight_kg) / 1000, 3), 0)
                           AS incoming_tons
                FROM stock_document d
                JOIN stock_document_item i ON i.document_id = d.id
                JOIN product p ON p.id = i.product_id
                WHERE d.type = 'INBOUND' AND d.status = 'DRAFT'
                """, new MapSqlParameterSource(),
                (rs, n) -> new DraftInbound(rs.getLong("drafts"), rs.getBigDecimal("incoming_tons")));
    }

    public long draftSalesCount() {
        return jdbc.queryForObject("SELECT count(*) FROM stock_document WHERE type = 'OUTBOUND' AND status = 'DRAFT'",
                new MapSqlParameterSource(), Long.class);
    }

    public record SalesTotals(BigDecimal revenue, long orderCount, BigDecimal tons) {
    }

    /** Doanh số, số phiếu, số tấn đã bán theo NGÀY BÁN trong [from, to]. */
    public SalesTotals salesTotals(LocalDate from, LocalDate to) {
        MapSqlParameterSource params = new MapSqlParameterSource("from", from).addValue("to", to);
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(d.total_amount), 0) AS revenue,
                       count(*)                          AS orders,
                       COALESCE((SELECT ROUND(SUM(i.quantity * p.net_weight_kg) / 1000, 3)
                                 FROM stock_document d
                                 JOIN stock_document_item i ON i.document_id = d.id
                                 JOIN product p ON p.id = i.product_id
                                 WHERE %1$s AND d.document_date BETWEEN :from AND :to), 0) AS tons
                FROM stock_document d
                WHERE %1$s AND d.document_date BETWEEN :from AND :to
                """.formatted(COMPLETED_SALE), params,
                (rs, n) -> new SalesTotals(rs.getBigDecimal("revenue"), rs.getLong("orders"), rs.getBigDecimal("tons")));
    }

    public record DailyRow(LocalDate date, BigDecimal revenue, long orderCount) {
    }

    /** Doanh số từng ngày có bán trong [from, to]; ngày không bán không có dòng (service điền 0). */
    public List<DailyRow> dailySales(LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT d.document_date, SUM(d.total_amount) AS revenue, count(*) AS orders
                FROM stock_document d
                WHERE %s AND d.document_date BETWEEN :from AND :to
                GROUP BY d.document_date
                ORDER BY d.document_date
                """.formatted(COMPLETED_SALE), new MapSqlParameterSource("from", from).addValue("to", to),
                (rs, n) -> new DailyRow(rs.getObject("document_date", LocalDate.class), rs.getBigDecimal("revenue"),
                        rs.getLong("orders")));
    }

    public record WarehouseRow(Long id, String code, String name, boolean active) {
    }

    /** Cột kho của bảng SP × kho: kho đang hoạt động + kho đã ngừng mà vẫn còn hàng (BR-STK-01). */
    public List<WarehouseRow> reportWarehouses() {
        return jdbc.query("""
                SELECT w.id, w.code, w.name, w.active
                FROM warehouse w
                WHERE w.active OR EXISTS (SELECT 1 FROM inventory i WHERE i.warehouse_id = w.id AND i.quantity > 0)
                ORDER BY w.code
                """, new MapSqlParameterSource(),
                (rs, n) -> new WarehouseRow(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                        rs.getBoolean("active")));
    }

    public record ProductInfo(Long id, String code, String name, String categoryName, String unitName,
                              BigDecimal netWeightKg, boolean active) {
    }

    public record StockCell(ProductInfo product, Long warehouseId, long quantity) {
    }

    /**
     * Tồn từng ô (SP, kho). SP không còn hàng ở kho nào vẫn có một dòng với warehouseId = null.
     * Lọc: danh mục, từ khóa (mã / tên, không phân biệt dấu); SP đã ngừng chỉ hiện khi còn hàng (lọc ở service).
     */
    public List<StockCell> stockCells(Long categoryId, String keyword) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String filters = productFilters(categoryId, keyword, params);
        return jdbc.query("""
                SELECT p.id, p.code, p.name, c.name AS category_name, u.name AS unit_name, p.net_weight_kg, p.active,
                       i.warehouse_id, COALESCE(i.quantity, 0) AS quantity
                FROM product p
                JOIN category c ON c.id = p.category_id
                JOIN product_unit u ON u.id = p.unit_id
                LEFT JOIN inventory i ON i.product_id = p.id AND i.quantity > 0
                WHERE TRUE %s
                ORDER BY p.code
                """.formatted(filters), params, (rs, n) -> new StockCell(productInfo(rs),
                rs.getObject("warehouse_id", Long.class), rs.getLong("quantity")));
    }

    public record MovementRow(ProductInfo product, long opening, long inbound, long outbound) {
    }

    /**
     * Nhập – xuất – tồn theo NGÀY GHI SỔ (stock_transaction.created_at) trong [start, end) (BR-RPT-05).
     * Giao dịch đảo bù trừ vào cột của giao dịch gốc: đảo một lần nhập (giao dịch OUT có reversal_of_id) làm GIẢM
     * cột Nhập chứ không cộng vào Xuất — phiếu nhập nhầm rồi đảo thì kỳ đó coi như không nhập.
     * Không cần JOIN sang giao dịch gốc: chiều của giao dịch đảo luôn ngược chiều giao dịch gốc.
     */
    public List<MovementRow> movement(Instant start, Instant end, Long warehouseId, Long categoryId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("start", Timestamp.from(start))
                .addValue("end", Timestamp.from(end));
        String warehouseFilter = "";
        if (warehouseId != null) {
            warehouseFilter = " AND t.warehouse_id = :warehouseId";
            params.addValue("warehouseId", warehouseId);
        }
        String filters = productFilters(categoryId, null, params);
        return jdbc.query("""
                WITH moves AS (
                    SELECT t.product_id,
                           SUM(CASE WHEN t.created_at < :start
                                    THEN CASE t.type WHEN 'IN' THEN t.quantity ELSE -t.quantity END
                                    ELSE 0 END) AS opening,
                           SUM(CASE WHEN t.created_at < :start THEN 0
                                    WHEN t.type = 'IN' AND t.reversal_of_id IS NULL THEN t.quantity
                                    WHEN t.type = 'OUT' AND t.reversal_of_id IS NOT NULL THEN -t.quantity
                                    ELSE 0 END) AS inbound,
                           SUM(CASE WHEN t.created_at < :start THEN 0
                                    WHEN t.type = 'OUT' AND t.reversal_of_id IS NULL THEN t.quantity
                                    WHEN t.type = 'IN' AND t.reversal_of_id IS NOT NULL THEN -t.quantity
                                    ELSE 0 END) AS outbound
                    FROM stock_transaction t
                    WHERE t.created_at < :end %s
                    GROUP BY t.product_id
                )
                SELECT p.id, p.code, p.name, c.name AS category_name, u.name AS unit_name, p.net_weight_kg, p.active,
                       m.opening, m.inbound, m.outbound
                FROM moves m
                JOIN product p ON p.id = m.product_id
                JOIN category c ON c.id = p.category_id
                JOIN product_unit u ON u.id = p.unit_id
                WHERE TRUE %s
                ORDER BY p.code
                """.formatted(warehouseFilter, filters), params, (rs, n) -> new MovementRow(productInfo(rs),
                rs.getLong("opening"), rs.getLong("inbound"), rs.getLong("outbound")));
    }

    private static String productFilters(Long categoryId, String keyword, MapSqlParameterSource params) {
        StringBuilder sql = new StringBuilder();
        if (categoryId != null) {
            sql.append(" AND p.category_id = :categoryId");
            params.addValue("categoryId", categoryId);
        }
        if (keyword != null && !keyword.isBlank()) {
            // Cùng cách chuẩn hóa với ô tìm kiếm các màn danh sách (CommonSpecifications.keywordContains).
            // Chuỗi thường, không dùng text block: text block cắt khoảng trắng đầu dòng → dính "...:categoryIdAND"
            sql.append(" AND (lower(immutable_unaccent(p.code)) LIKE lower(immutable_unaccent(:pattern)) ESCAPE '\\'")
                    .append(" OR lower(immutable_unaccent(p.name)) LIKE lower(immutable_unaccent(:pattern)) ESCAPE '\\')");
            params.addValue("pattern", CommonSpecifications.containsPattern(keyword));
        }
        return sql.toString();
    }

    private static ProductInfo productInfo(ResultSet rs) throws SQLException {
        return new ProductInfo(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                rs.getString("category_name"), rs.getString("unit_name"), rs.getBigDecimal("net_weight_kg"),
                rs.getBoolean("active"));
    }
}
