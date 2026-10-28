package com.grainwarehouse.product.entity;

import com.grainwarehouse.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;

import java.math.BigDecimal;

/** Sản phẩm cám (BR-PRD-01..08). */
@Getter
@Setter
@Entity
@Table(name = "product")
public class Product extends AuditableEntity {

    // DB tự sinh qua DEFAULT nextval(...) (BR-PRD-02):
    // - @Generated (Hibernate 6.2+): không gửi cột trong INSERT để DEFAULT có hiệu lực,
    //   rồi đọc lại giá trị DB vừa sinh. Thiếu nó, Hibernate gửi code = NULL → lỗi NOT NULL
    //   (đã kiểm chứng bằng ProductPersistenceTest)
    // - insertable/updatable = false: khai báo tường minh (thừa với insertable, nhưng rõ ý đồ);
    //   cùng với việc không có setter → mã không sửa được
    @Generated
    @Setter(AccessLevel.NONE)
    @Column(nullable = false, length = 20, insertable = false, updatable = false)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    // LAZY: chỉ truy vấn category khi thực sự cần
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    // EAGER (khác category): tên đơn vị có trong hầu hết response chứng từ/tồn kho; open-in-view tắt nên LAZY
    // dễ LazyInitializationException ở chỗ quên JOIN FETCH. Chỉ vài đơn vị → mỗi request tối đa vài SELECT nhỏ,
    // Hibernate cache trong persistence context nên không thành N+1 theo số SP
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "unit_id", nullable = false)
    private ProductUnit unit;

    @Column(name = "net_weight_kg", nullable = false, precision = 10, scale = 3)
    private BigDecimal netWeightKg;

    @Column(columnDefinition = "TEXT")
    private String description;

    // BR-STK-03: mức tồn tối thiểu (cùng đơn vị với unit); null = không cảnh báo Sắp hết
    @Column(name = "min_stock_quantity")
    private Integer minStockQuantity;

    @Column(nullable = false)
    private boolean active = true;
}
