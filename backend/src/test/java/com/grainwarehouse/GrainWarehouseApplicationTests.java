package com.grainwarehouse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class GrainWarehouseApplicationTests {

    @Test
    void contextLoads() {
        // Context khởi động với DB test thật: Flyway chạy migration và
        // Hibernate (ddl-auto: validate) kiểm tra Entity khớp schema
    }
}
