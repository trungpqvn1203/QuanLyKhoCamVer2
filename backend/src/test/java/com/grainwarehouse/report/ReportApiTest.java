package com.grainwarehouse.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grainwarehouse.common.search.CommonSpecifications;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Báo cáo tồn kho và tổng quan (BR-RPT) qua luồng chứng từ thật. DB test dùng chung nên báo cáo lọc theo danh mục
 * riêng của test; tổng quan (không lọc được) so CHÊNH LỆCH trước / sau.
 *
 * <pre>
 * Chứng từ (hôm nay)                   Cám heo (bao 25kg)     Cám cá (bao 25kg)
 * PN-1 Kho HN: heo 20, cá 10           HN 20                  HN 10
 * PX-1 Kho HN: heo 5                   HN 15
 * PN-2 Kho HN: heo 4 → đảo phiếu       HN 19 → 15             (nhập rồi đảo = không nhập)
 * PX-2 Kho HN: heo 3 → đảo phiếu       HN 12 → 15             (bán rồi đảo = không xuất)
 * PN-3 Kho HP: heo 7                   HP 7
 * Cuối: heo 22 (HN 15 + HP 7), cá 10. Nhập heo = 27, xuất heo = 5.
 * </pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReportApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long category;
    private long pig;
    private long fish;
    private long hanoi;
    private long haiphong;
    private long supplier;
    private long customer;
    private LocalDate today;

    @BeforeEach
    void setUp() throws Exception {
        today = LocalDate.now(CommonSpecifications.BUSINESS_ZONE);
        category = id("INSERT INTO category (name) VALUES ('Cám test báo cáo') RETURNING id");
        pig = product("Cám heo báo cáo");
        fish = product("Cám cá báo cáo");
        hanoi = id("INSERT INTO warehouse (name, type, address) VALUES ('Kho HN báo cáo', 'CENTRAL', 'HN') RETURNING id");
        haiphong = id("INSERT INTO warehouse (name, type, address) VALUES ('Kho HP báo cáo', 'RETAIL', 'HP') RETURNING id");
        supplier = id("INSERT INTO supplier (name) VALUES ('NCC báo cáo') RETURNING id");
        customer = id("INSERT INTO customer (name, type, phone) VALUES ('Đại lý báo cáo', 'DEALER', '0909000222')"
                + " RETURNING id");
        jdbcTemplate.update("INSERT INTO product_supplier (supplier_id, product_id) VALUES (?, ?), (?, ?)",
                supplier, pig, supplier, fish);

        completeReceipt(hanoi, Map.of(pig, 20, fish, 10));
        completeSale(Map.of(pig, 5));
        long secondReceipt = completeReceipt(hanoi, Map.of(pig, 4)).get("id").asLong();
        perform(post("/api/inbound-receipts/{id}/reverse", secondReceipt).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"Nhập nhầm\"}"));
        long secondSale = completeSale(Map.of(pig, 3)).get("id").asLong();
        perform(post("/api/sales-orders/{id}/reverse", secondSale).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"Khách trả hàng\"}"));
        completeReceipt(haiphong, Map.of(pig, 7));
    }

    // ===== Tồn hiện tại SP × kho =====

    @Test
    @DisplayName("BR-RPT-04: SP × kho - SL từng kho, tổng, tấn; dòng tổng mỗi kho chỉ có tấn")
    void shouldReportStockByWarehouse() throws Exception {
        mockMvc.perform(get("/api/reports/stock-by-warehouse").param("categoryId", String.valueOf(category)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows.length()").value(2))
                .andExpect(jsonPath("$.data.rows[?(@.productId == %d)].quantities.%d".formatted(pig, hanoi)).value(15))
                .andExpect(jsonPath("$.data.rows[?(@.productId == %d)].quantities.%d".formatted(pig, haiphong)).value(7))
                .andExpect(jsonPath("$.data.rows[?(@.productId == %d)].totalQuantity".formatted(pig)).value(22))
                .andExpect(jsonPath("$.data.rows[?(@.productId == %d)].totalTons".formatted(pig)).value(0.55))
                .andExpect(jsonPath("$.data.rows[?(@.productId == %d)].quantities.%d".formatted(fish, haiphong))
                        .isEmpty())
                .andExpect(jsonPath("$.data.warehouseTons.%d".formatted(hanoi)).value(0.625))
                .andExpect(jsonPath("$.data.warehouseTons.%d".formatted(haiphong)).value(0.175))
                .andExpect(jsonPath("$.data.totalTons").value(0.8))
                .andExpect(jsonPath("$.data.warehouses[?(@.id == %d)].code".formatted(hanoi)).isNotEmpty());
    }

    @Test
    @DisplayName("BR-RPT-04: SP hết hàng ẩn mặc định, hideZero=false thì hiện; tìm không dấu theo tên")
    void shouldHideZeroStockByDefault() throws Exception {
        product("Cám không tồn báo cáo");

        mockMvc.perform(get("/api/reports/stock-by-warehouse").param("categoryId", String.valueOf(category)))
                .andExpect(jsonPath("$.data.rows.length()").value(2));
        mockMvc.perform(get("/api/reports/stock-by-warehouse").param("categoryId", String.valueOf(category))
                        .param("hideZero", "false").param("keyword", "khong ton"))
                .andExpect(jsonPath("$.data.rows.length()").value(1))
                .andExpect(jsonPath("$.data.rows[0].totalQuantity").value(0));
    }

    // ===== Nhập – xuất – tồn =====

    @Test
    @DisplayName("BR-RPT-05: kỳ hôm nay - đầu 0, nhập 27, xuất 5 (phiếu đã đảo bù trừ), cuối 22 = tồn hiện tại")
    void shouldReportMovementWithReversalsNetted() throws Exception {
        movement(today, today, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows.length()").value(2))
                .andExpect(jsonPath(row(pig, "openingQuantity")).value(0))
                .andExpect(jsonPath(row(pig, "inboundQuantity")).value(27))
                .andExpect(jsonPath(row(pig, "outboundQuantity")).value(5))
                .andExpect(jsonPath(row(pig, "closingQuantity")).value(22))
                .andExpect(jsonPath(row(pig, "closingTons")).value(0.55))
                .andExpect(jsonPath(row(fish, "closingQuantity")).value(10))
                .andExpect(jsonPath("$.data.totals.closingTons").value(0.8));

        Integer current = jdbcTemplate.queryForObject(
                "SELECT SUM(quantity)::int FROM inventory WHERE product_id = ?", Integer.class, pig);
        assertThat(current).isEqualTo(22);
    }

    @Test
    @DisplayName("BR-RPT-05: kỳ sau hôm nay - tồn đầu = tồn cuối kỳ trước, không phát sinh")
    void shouldCarryClosingIntoNextPeriod() throws Exception {
        LocalDate tomorrow = today.plusDays(1);
        movement(tomorrow, tomorrow, null)
                .andExpect(jsonPath(row(pig, "openingQuantity")).value(22))
                .andExpect(jsonPath(row(pig, "inboundQuantity")).value(0))
                .andExpect(jsonPath(row(pig, "outboundQuantity")).value(0))
                .andExpect(jsonPath(row(pig, "closingQuantity")).value(22));
    }

    @Test
    @DisplayName("BR-RPT-05: lọc một kho - chỉ giao dịch của kho đó; SP không phát sinh ở kho đó thì không có dòng")
    void shouldFilterMovementByWarehouse() throws Exception {
        movement(today, today, haiphong)
                .andExpect(jsonPath("$.data.rows.length()").value(1))
                .andExpect(jsonPath(row(pig, "inboundQuantity")).value(7))
                .andExpect(jsonPath(row(pig, "closingQuantity")).value(7));
    }

    @Test
    @DisplayName("Nhập – xuất – tồn: thiếu ngày - 400 field from; đến trước từ - 400 field to")
    void shouldValidateMovementDates() throws Exception {
        mockMvc.perform(get("/api/reports/stock-movement").param("to", today.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("from"));
        movement(today, today.minusDays(1), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("to"));
    }

    // ===== Tổng quan =====

    @Test
    @DisplayName("BR-RPT-02: tổng quan - bán thêm 1 phiếu hôm nay: doanh số, số phiếu, tấn tăng đúng; nháp đếm riêng")
    void shouldReflectNewSaleOnDashboard() throws Exception {
        JsonNode before = data(perform(get("/api/reports/dashboard")));

        completeSale(Map.of(pig, 2));                       // 2 × 150.000 = 300.000; 2 × 25 kg = 0,05 tấn
        createDraftSale(Map.of(fish, 1));

        JsonNode after = data(perform(get("/api/reports/dashboard")));
        assertThat(after.get("today").asText()).isEqualTo(today.toString());
        assertThat(delta(before, after, "/sales/today/revenue")).isEqualByComparingTo("300000");
        assertThat(delta(before, after, "/sales/today/orderCount")).isEqualByComparingTo("1");
        assertThat(delta(before, after, "/sales/today/tons")).isEqualByComparingTo("0.05");
        assertThat(delta(before, after, "/sales/thisMonth/revenue")).isEqualByComparingTo("300000");
        assertThat(delta(before, after, "/sales/draftCount")).isEqualByComparingTo("1");
        assertThat(delta(before, after, "/stock/totalTons")).isEqualByComparingTo("-0.05");

        JsonNode days = after.at("/sales/last30Days");
        assertThat(days).hasSize(30);
        assertThat(days.get(29).get("date").asText()).isEqualTo(today.toString());
        assertThat(days.get(29).get("revenue").decimalValue())
                .isEqualByComparingTo(after.at("/sales/today/revenue").decimalValue());
    }

    @Test
    @DisplayName("BR-RPT-01: tổng quan - SP đang kinh doanh hết hàng / sắp hết được đếm")
    void shouldCountStockAlerts() throws Exception {
        JsonNode before = data(perform(get("/api/reports/dashboard")));

        product("Cám hết hàng báo cáo");                    // tồn 0 → hết hàng
        jdbcTemplate.update("UPDATE product SET min_stock_quantity = 10 WHERE id = ?", fish); // tồn 10 ≤ 10 → sắp hết

        JsonNode after = data(perform(get("/api/reports/dashboard")));
        assertThat(delta(before, after, "/stock/productCount")).isEqualByComparingTo("1");
        assertThat(delta(before, after, "/stock/outOfStockCount")).isEqualByComparingTo("1");
        assertThat(delta(before, after, "/stock/lowStockCount")).isEqualByComparingTo("1");
    }

    // ===== Helpers =====

    private static String row(long productId, String field) {
        return "$.data.rows[?(@.productId == %d)].%s".formatted(productId, field);
    }

    private ResultActions movement(LocalDate from, LocalDate to, Long warehouseId) throws Exception {
        var request = get("/api/reports/stock-movement").param("from", from.toString()).param("to", to.toString())
                .param("categoryId", String.valueOf(category));
        if (warehouseId != null) {
            request.param("warehouseId", String.valueOf(warehouseId));
        }
        return mockMvc.perform(request);
    }

    private static BigDecimal delta(JsonNode before, JsonNode after, String pointer) {
        return after.at(pointer).decimalValue().subtract(before.at(pointer).decimalValue());
    }

    private long product(String name) {
        return id("INSERT INTO product (name, category_id, unit_id, net_weight_kg)"
                + " VALUES (?, ?, (SELECT id FROM product_unit WHERE name = 'Bao'), 25) RETURNING id", name, category);
    }

    private JsonNode completeReceipt(long warehouseId, Map<Long, Integer> quantities) throws Exception {
        String lines = quantities.entrySet().stream()
                .map(e -> "{\"productId\": " + e.getKey() + ", \"orderedQuantity\": " + e.getValue()
                        + ", \"unitPrice\": 100000}")
                .reduce((a, b) -> a + ", " + b).orElseThrow();
        String body = "{\"supplierId\": " + supplier + ", \"warehouseId\": " + warehouseId
                + ", \"lines\": [" + lines + "]}";
        long id = data(perform(post("/api/inbound-receipts").contentType(MediaType.APPLICATION_JSON).content(body)))
                .get("id").asLong();
        return data(perform(post("/api/inbound-receipts/{id}/complete", id)));
    }

    // Mọi phiếu bán trong test xuất từ Kho HN, giá 150.000
    private long createDraftSale(Map<Long, Integer> quantities) throws Exception {
        String lines = quantities.entrySet().stream()
                .map(e -> "{\"productId\": " + e.getKey() + ", \"quantity\": " + e.getValue() + ", \"unitPrice\": 150000}")
                .reduce((a, b) -> a + ", " + b).orElseThrow();
        String body = "{\"customerId\": " + customer + ", \"warehouseId\": " + hanoi + ", \"lines\": [" + lines + "]}";
        return data(perform(post("/api/sales-orders").contentType(MediaType.APPLICATION_JSON).content(body)))
                .get("id").asLong();
    }

    private JsonNode completeSale(Map<Long, Integer> quantities) throws Exception {
        return data(perform(post("/api/sales-orders/{id}/complete", createDraftSale(quantities))));
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request).andExpect(status().is2xxSuccessful());
    }

    private JsonNode data(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("data");
    }

    private long id(String sql, Object... args) {
        Long id = jdbcTemplate.queryForObject(sql, Long.class, args);
        assertThat(id).isNotNull();
        return id;
    }
}
