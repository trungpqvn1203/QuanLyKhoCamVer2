package com.grainwarehouse.customer.entity;

import com.grainwarehouse.common.entity.AuditableEntity;
import com.grainwarehouse.pricelist.entity.PriceList;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;

/** Khách hàng (BR-CUS-01..06). */
@Getter
@Setter
@Entity
@Table(name = "customer")
public class Customer extends AuditableEntity {

    // DB tự sinh qua DEFAULT nextval(...) (BR-CUS-02) — cùng cách với Supplier.code
    @Generated
    @Setter(AccessLevel.NONE)
    @Column(nullable = false, length = 20, insertable = false, updatable = false)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomerType type;

    // Luôn ở dạng đã chuẩn hóa (BR-CUS-06) — service gọi PhoneNumbers.normalize trước khi set
    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "contact_name", length = 200)
    private String contactName;

    @Column(length = 500)
    private String address;

    @Column(length = 1000)
    private String note;

    // BR-PRC-04: bảng giá áp dụng cho khách (tùy chọn). EAGER giống Product.unit vì chỉ có vài bảng giá
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "price_list_id")
    private PriceList priceList;

    @Column(nullable = false)
    private boolean active = true;
}
