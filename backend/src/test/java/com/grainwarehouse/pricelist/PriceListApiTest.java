package com.grainwarehouse.pricelist;

import com.grainwarehouse.product.entity.Category;
import com.grainwarehouse.product.entity.Product;
import com.grainwarehouse.product.repository.CategoryRepository;
import com.grainwarehouse.product.repository.ProductRepository;
import com.grainwarehouse.product.repository.ProductUnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Nhiều bảng giá bán cho sản phẩm và gán bảng giá cho khách hàng (BR-PRC-01..07). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PriceListApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductUnitRepository unitRepository;

    @Autowired
    private ProductRepository productRepository;

    private Product pigFeed;
    private Product chickenFeed;

    @BeforeEach
    void setUp() {
        Category category = new Category();
        category.setName("Cám heo bảng giá");
        category = categoryRepository.save(category);

        pigFeed = saveProduct("Cám heo con 25kg", category);
        chickenFeed = saveProduct("Cám gà thịt 25kg", category);
    }

    @Test
    @DisplayName("BR-PRC-01..03: tạo, sửa, ngừng, mở lại bảng giá; chặn trùng tên không phân biệt hoa thường")
    void shouldManagePriceLists() throws Exception {
        long retailId = createdId(createPriceList("Giá lẻ", "Áp dụng cho khách mua lẻ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Giá lẻ"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.itemCount").value(0))
                .andExpect(jsonPath("$.data.customerCount").value(0)));

        // Trùng tên (khác hoa thường) → 409
        createPriceList("giá LẺ", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_RESOURCE"));

        // Ngừng dùng → tạo lại tên cũ vẫn bị chặn và hướng dẫn mở lại
        mockMvc.perform(delete("/api/price-lists/{id}", retailId))
                .andExpect(status().isNoContent());

        createPriceList("Giá lẻ", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("Hãy mở lại thay vì tạo mới")));

        // Mở lại bằng PUT active=true
        mockMvc.perform(put("/api/price-lists/{id}", retailId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Giá bán lẻ", "description": "Khách mua lẻ", "active": true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Giá bán lẻ"))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    @DisplayName("BR-PRC-02: một SP có nhiều đơn giá ở các bảng giá khác nhau; đặt, ghi đè, xóa và đọc theo ma trận")
    void shouldManageMultiplePricesPerProductAcrossLists() throws Exception {
        long retailId = createdId(createPriceList("Giá lẻ", null));
        long dealerId = createdId(createPriceList("Giá đại lý", null));
        long farmId = createdId(createPriceList("Giá trang trại", null));

        // Cùng 1 SP (pigFeed) có 3 mức giá ở 3 bảng giá khác nhau
        setPrice(retailId, pigFeed.getId(), "360000")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unitPrice").value(360000));
        setPrice(dealerId, pigFeed.getId(), "340000").andExpect(status().isOk());
        setPrice(farmId, pigFeed.getId(), "345000.5").andExpect(status().isOk());
        setPrice(retailId, chickenFeed.getId(), "320000").andExpect(status().isOk());

        // Ghi đè giá đại lý
        setPrice(dealerId, pigFeed.getId(), "338000")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unitPrice").value(338000));

        // Đọc toàn bộ giá của một bảng giá
        mockMvc.perform(get("/api/price-lists/{id}/items", retailId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        // Đọc ma trận theo danh sách productIds
        mockMvc.perform(get("/api/price-lists/items")
                        .param("productIds", pigFeed.getId().toString(), chickenFeed.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4));

        // Ưu tiên xếp sản phẩm đã có giá trong bảng giá lên đầu (chickenFeed chưa có giá ở dealerId, pigFeed đã có giá)
        mockMvc.perform(get("/api/products")
                        .param("prioritizePriceListId", Long.toString(dealerId))
                        .param("sort", "id,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(pigFeed.getId()));

        // Bỏ giá 1 SP trong bảng giá
        mockMvc.perform(delete("/api/price-lists/{listId}/items/{productId}", retailId, chickenFeed.getId()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/price-lists/{id}/items", retailId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @DisplayName("BR-PRC-04: gán bảng giá cho khách hàng; chặn gán mới bảng giá đã ngừng dùng")
    void shouldAssignPriceListToCustomer() throws Exception {
        long dealerListId = createdId(createPriceList("Giá đại lý cấp 1", null));
        long stoppedListId = createdId(createPriceList("Giá cũ ngừng dùng", null));
        mockMvc.perform(delete("/api/price-lists/{id}", stoppedListId)).andExpect(status().isNoContent());

        // Tạo khách với bảng giá đã ngừng → 409
        mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Đại lý A", "type": "DEALER", "phone": "0901234567", "priceListId": %d}
                        """.formatted(stoppedListId)))
                .andExpect(status().isConflict());

        // Tạo khách với bảng giá đang hoạt động → 201, kèm tên bảng giá
        long customerId = createdId(mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Đại lý A", "type": "DEALER", "phone": "0901234567", "priceListId": %d}
                        """.formatted(dealerListId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.priceListId").value(dealerListId))
                .andExpect(jsonPath("$.data.priceListName").value("Giá đại lý cấp 1")));

        // customerCount của bảng giá tăng lên 1
        mockMvc.perform(get("/api/price-lists").param("keyword", "đại lý cấp 1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(dealerListId))
                .andExpect(jsonPath("$.data.items[0].customerCount").value(1));

        // Bỏ bảng giá khỏi khách (PUT không gửi priceListId)
        mockMvc.perform(put("/api/customers/{id}", customerId).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Đại lý A", "type": "DEALER", "phone": "0901234567", "active": true}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.priceListId").doesNotExist());
    }

    private ResultActions createPriceList(String name, String description) throws Exception {
        String descJson = description == null ? "" : ", \"description\": \"%s\"".formatted(description);
        return mockMvc.perform(post("/api/price-lists")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"%s\"%s}".formatted(name, descJson)));
    }

    private ResultActions setPrice(long priceListId, long productId, String unitPrice) throws Exception {
        return mockMvc.perform(put("/api/price-lists/{listId}/items/{productId}", priceListId, productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"unitPrice\": %s}".formatted(unitPrice)));
    }

    private Product saveProduct(String name, Category category) {
        Product product = new Product();
        product.setName(name);
        product.setCategory(category);
        product.setUnit(unitRepository.findByNameIgnoreCase("Bao").orElseThrow());
        product.setNetWeightKg(new BigDecimal("25"));
        return productRepository.save(product);
    }

    private static long createdId(ResultActions action) throws Exception {
        String body = action.andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }
}
