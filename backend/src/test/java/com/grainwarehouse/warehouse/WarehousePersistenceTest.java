package com.grainwarehouse.warehouse;

import com.grainwarehouse.warehouse.entity.Warehouse;
import com.grainwarehouse.warehouse.entity.WarehouseType;
import com.grainwarehouse.warehouse.repository.WarehouseRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kiểm chứng ràng buộc ở tầng DATABASE (migration V3), bỏ qua validation của Java
 * bằng cách INSERT thẳng qua JdbcTemplate.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WarehousePersistenceTest {

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("BR-WH-02: save() xong đọc được ngay mã kho do DB tự sinh (@Generated)")
    void shouldReadDbGeneratedCodeRightAfterSave() {
        Warehouse warehouse = new Warehouse();
        warehouse.setName("Kho Hà Nội");
        warehouse.setType(WarehouseType.CENTRAL);
        warehouse.setAddress("Hà Nội");

        Warehouse saved = warehouseRepository.save(warehouse);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCode()).matches("KHO\\d{3,}");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("BR-WH-03: DB chặn tên kho trùng dù khác hoa thường")
    void dbShouldRejectDuplicateNameIgnoringCase() {
        insertWarehouse("Kho Hà Nội", "CENTRAL", "Hà Nội");

        assertThatThrownBy(() -> insertWarehouse("KHO HÀ NỘI", "RETAIL", "Hà Nội"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_warehouse_name");
    }

    @Test
    @DisplayName("BR-WH-01: DB chặn loại kho ngoài CENTRAL/RETAIL")
    void dbShouldRejectUnknownType() {
        assertThatThrownBy(() -> insertWarehouse("Kho Hà Nội", "WHOLESALE", "Hà Nội"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_warehouse_type");
    }

    @Test
    @DisplayName("BR-WH-01: DB chặn kho thiếu địa chỉ")
    void dbShouldRejectMissingAddress() {
        assertThatThrownBy(() -> insertWarehouse("Kho Hà Nội", "CENTRAL", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("address");
    }

    private void insertWarehouse(String name, String type, String address) {
        jdbcTemplate.update("INSERT INTO warehouse (name, type, address) VALUES (?, ?, ?)", name, type, address);
    }
}
