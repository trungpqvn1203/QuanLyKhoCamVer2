package com.grainwarehouse.inventory;

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

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lịch sử biến động tồn (BR-INV-04..06) qua luồng chứng từ thật — tra được "vì sao Cám heo ở Kho HN còn 27".
 *
 * <pre>
 * Bước  Chứng từ                         Kho HN – Cám heo
 * 1     PN-1 hoàn tất: heo 20, gà 10      IN  20   0 → 20
 * 2     PX-1 hoàn tất: heo 5              OUT  5  20 → 15
 * 3     PN-2 hoàn tất: heo 12             IN  12  15 → 27
 * 4     PX-2 hoàn tất: heo 3              OUT  3  27 → 24
 * 5     Đảo PX-2                          IN   3  24 → 27  (reversalOf = bước 4)
 * —     PN-3 hoàn tất tại Kho HP: heo 7   (kho khác, không lẫn vào lịch sử Kho HN)
 * </pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StockTransactionApiTest {

    private static final String BASE = "/api/inventory/transactions";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long pig;
    private long chicken;
    private long hanoi;
    private long haiphong;
    private long supplier;
    private long customer;
    private String firstReceiptCode;

    @BeforeEach
    void setUp() throws Exception {
        long category = id("INSERT INTO category (name) VALUES ('Cám test lịch sử') RETURNING id");
        pig = id("INSERT INTO product (name, category_id, unit, net_weight_kg) VALUES ('Cám heo lịch sử', ?, 'BAG', 25)"
                + " RETURNING id", category);
        chicken = id("INSERT INTO product (name, category_id, unit, net_weight_kg) VALUES ('Cám gà lịch sử', ?, 'BAG', 25)"
                + " RETURNING id", category);
        hanoi = id("INSERT INTO warehouse (name, type, address) VALUES ('Kho HN lịch sử', 'CENTRAL', 'HN') RETURNING id");
        haiphong = id("INSERT INTO warehouse (name, type, address) VALUES ('Kho HP lịch sử', 'RETAIL', 'HP') RETURNING id");
        supplier = id("INSERT INTO supplier (name) VALUES ('NCC lịch sử') RETURNING id");
        customer = id("INSERT INTO customer (name, type, phone) VALUES ('Đại lý lịch sử', 'DEALER', '0909000111')"
                + " RETURNING id");
        jdbcTemplate.update("INSERT INTO product_supplier (supplier_id, product_id) VALUES (?, ?), (?, ?)",
                supplier, pig, supplier, chicken);

        JsonNode firstReceipt = completeReceipt(hanoi, Map.of(pig, 20, chicken, 10));
        firstReceiptCode = firstReceipt.get("code").asText();
        completeSale(Map.of(pig, 5));
        completeReceipt(hanoi, Map.of(pig, 12));
        long secondSale = completeSale(Map.of(pig, 3)).get("id").asLong();
        perform(post("/api/sales-orders/{id}/reverse", secondSale).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"Khách trả hàng\"}"));
        completeReceipt(haiphong, Map.of(pig, 7));
    }

    @Test
    @DisplayName("BR-INV-04, 06: lịch sử một SP tại một kho - mới nhất trước, tồn trước/sau nối liền, dòng đầu = tồn hiện tại")
    void shouldExplainCurrentStock() throws Exception {
        String body = mockMvc.perform(get(BASE)
                        .param("productId", String.valueOf(pig))
                        .param("warehouseId", String.valueOf(hanoi)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pagination.totalElements").value(5))
                // Bước 5: đảo PX-2 → IN 24 → 27, trỏ về giao dịch bước 4
                .andExpect(jsonPath("$.data.items[0].type").value("IN"))
                .andExpect(jsonPath("$.data.items[0].quantity").value(3))
                .andExpect(jsonPath("$.data.items[0].quantityBefore").value(24))
                .andExpect(jsonPath("$.data.items[0].quantityAfter").value(27))
                .andExpect(jsonPath("$.data.items[0].documentType").value("OUTBOUND"))
                .andExpect(jsonPath("$.data.items[0].customerName").value("Đại lý lịch sử"))
                .andExpect(jsonPath("$.data.items[0].supplierName").doesNotExist())
                .andExpect(jsonPath("$.data.items[1].reversalOfId").doesNotExist())
                // Bước 1: phiếu nhập đầu tiên
                .andExpect(jsonPath("$.data.items[4].type").value("IN"))
                .andExpect(jsonPath("$.data.items[4].quantityBefore").value(0))
                .andExpect(jsonPath("$.data.items[4].quantityAfter").value(20))
                .andExpect(jsonPath("$.data.items[4].documentCode").value(firstReceiptCode))
                .andExpect(jsonPath("$.data.items[4].documentType").value("INBOUND"))
                .andExpect(jsonPath("$.data.items[4].supplierName").value("NCC lịch sử"))
                .andExpect(jsonPath("$.data.items[4].customerName").doesNotExist())
                .andExpect(jsonPath("$.data.items[4].productCode").exists())
                .andExpect(jsonPath("$.data.items[4].unit").value("BAG"))
                .andExpect(jsonPath("$.data.items[4].warehouseName").value("Kho HN lịch sử"))
                .andReturn().getResponse().getContentAsString();

        JsonNode items = objectMapper.readTree(body).at("/data/items");
        assertThat(items.get(0).get("reversalOfId").asLong()).isEqualTo(items.get(1).get("id").asLong());
        // Sổ cái không đứt quãng: tồn trước của dòng mới = tồn sau của dòng liền trước nó
        for (int i = 0; i < items.size() - 1; i++) {
            assertThat(items.get(i).get("quantityBefore").asInt()).isEqualTo(items.get(i + 1).get("quantityAfter").asInt());
        }
        Integer stock = jdbcTemplate.queryForObject(
                "SELECT quantity FROM inventory WHERE product_id = ? AND warehouse_id = ?", Integer.class, pig, hanoi);
        assertThat(items.get(0).get("quantityAfter").asInt()).isEqualTo(stock).isEqualTo(27);
    }

    @Test
    @DisplayName("Lọc SP không kèm kho → gồm mọi kho; lọc theo kho → chỉ kho đó")
    void shouldFilterByProductAndWarehouse() throws Exception {
        perform(get(BASE).param("productId", String.valueOf(pig)))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(6));
        perform(get(BASE).param("productId", String.valueOf(pig)).param("warehouseId", String.valueOf(haiphong)))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].quantityAfter").value(7));
        perform(get(BASE).param("productId", String.valueOf(chicken)))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(1));
    }

    @Test
    @DisplayName("Lọc theo mã chứng từ, chiều IN/OUT và loại chứng từ")
    void shouldFilterByDocumentAndType() throws Exception {
        // Phiếu nhập đầu có 2 dòng → 2 giao dịch; gõ thường vẫn khớp mã
        perform(get(BASE).param("keyword", firstReceiptCode.toLowerCase()))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(2));
        perform(get(BASE).param("productId", String.valueOf(pig)).param("documentType", "OUTBOUND"))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(3));
        // Giao dịch IN của phiếu bán chỉ có thể là giao dịch đảo
        perform(get(BASE).param("productId", String.valueOf(pig)).param("documentType", "OUTBOUND").param("type", "IN"))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].reversalOfId").exists());
        perform(get(BASE).param("productId", String.valueOf(pig)).param("type", "OUT"))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(2));
    }

    @Test
    @DisplayName("Lọc theo ngày ghi sổ (giờ VN)")
    void shouldFilterByCreatedDate() throws Exception {
        LocalDate today = LocalDate.now(CommonSpecifications.BUSINESS_ZONE);
        perform(get(BASE).param("productId", String.valueOf(pig))
                .param("createdFrom", today.toString()).param("createdTo", today.toString()))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(6));
        perform(get(BASE).param("productId", String.valueOf(pig)).param("createdFrom", today.plusDays(1).toString()))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(0));
        perform(get(BASE).param("productId", String.valueOf(pig)).param("createdTo", today.minusDays(1).toString()))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(0));
    }

    @Test
    @DisplayName("Sai kiểu tham số → 400 VALIDATION_FAILED")
    void shouldRejectInvalidParameters() throws Exception {
        mockMvc.perform(get(BASE).param("type", "SIDEWAYS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        mockMvc.perform(get(BASE).param("createdFrom", "20/10/2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("BR-INV-05: lịch sử chỉ đọc - không có POST")
    void shouldBeReadOnly() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    // ===== Helpers =====

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

    // Mọi phiếu bán trong test xuất từ Kho HN
    private JsonNode completeSale(Map<Long, Integer> quantities) throws Exception {
        String lines = quantities.entrySet().stream()
                .map(e -> "{\"productId\": " + e.getKey() + ", \"quantity\": " + e.getValue() + ", \"unitPrice\": 150000}")
                .reduce((a, b) -> a + ", " + b).orElseThrow();
        String body = "{\"customerId\": " + customer + ", \"warehouseId\": " + hanoi + ", \"lines\": [" + lines + "]}";
        long id = data(perform(post("/api/sales-orders").contentType(MediaType.APPLICATION_JSON).content(body)))
                .get("id").asLong();
        return data(perform(post("/api/sales-orders/{id}/complete", id)));
    }

    private ResultActions perform(MockHttpServletRequestBuilder request)
            throws Exception {
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
