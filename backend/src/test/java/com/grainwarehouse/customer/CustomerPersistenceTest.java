package com.grainwarehouse.customer;

import com.grainwarehouse.customer.entity.Customer;
import com.grainwarehouse.customer.entity.CustomerType;
import com.grainwarehouse.customer.repository.CustomerRepository;
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
 * Kiểm chứng ràng buộc ở tầng DATABASE (migration V8), bỏ qua validation của Java
 * bằng cách INSERT thẳng qua JdbcTemplate.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CustomerPersistenceTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("BR-CUS-02: save() xong đọc được ngay mã KH do DB tự sinh dạng KH + 4 chữ số")
    void shouldReadDbGeneratedCodeRightAfterSave() {
        Customer customer = new Customer();
        customer.setName("Đại lý Minh Phát");
        customer.setType(CustomerType.DEALER);
        customer.setPhone("0901234567");

        Customer saved = customerRepository.save(customer);

        assertThat(saved.getCode()).matches("KH\\d{4,}");
    }

    @Test
    @DisplayName("BR-CUS-04: DB chặn SĐT trùng; tên trùng thì được")
    void dbShouldRejectDuplicatePhoneButAllowDuplicateName() {
        insertCustomer("Trại heo Minh", "FARM", "0901234567");
        insertCustomer("Trại heo Minh", "FARM", "0912345678");

        assertThatThrownBy(() -> insertCustomer("Khách khác", "RETAIL", "0901234567"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_customer_phone");
    }

    // Mỗi vi phạm một test: sau lỗi đầu tiên PostgreSQL hủy cả transaction (25P02),
    // câu INSERT thứ hai trong cùng test sẽ không còn chạy được để kiểm
    @Test
    @DisplayName("BR-CUS-06: DB chặn SĐT chưa chuẩn hóa (còn khoảng trắng)")
    void dbShouldRejectUnnormalizedPhone() {
        assertThatThrownBy(() -> insertCustomer("Đại lý A", "DEALER", "0901 234 567"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_customer_phone");
    }

    @Test
    @DisplayName("BR-CUS-06: DB chặn SĐT sai định dạng (thiếu số 0 đầu)")
    void dbShouldRejectPhoneWithoutLeadingZero() {
        assertThatThrownBy(() -> insertCustomer("Đại lý A", "DEALER", "901234567"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_customer_phone");
    }

    @Test
    @DisplayName("BR-CUS-03: DB chặn loại khách ngoài DEALER/FARM/RETAIL")
    void dbShouldRejectUnknownType() {
        assertThatThrownBy(() -> insertCustomer("Đại lý A", "WHOLESALE", "0901234567"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_customer_type");
    }

    @Test
    @DisplayName("BR-CUS-01: DB chặn khách thiếu SĐT")
    void dbShouldRejectMissingPhone() {
        assertThatThrownBy(() -> insertCustomer("Đại lý A", "DEALER", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("phone");
    }

    private void insertCustomer(String name, String type, String phone) {
        jdbcTemplate.update("INSERT INTO customer (name, type, phone) VALUES (?, ?, ?)", name, type, phone);
    }
}
