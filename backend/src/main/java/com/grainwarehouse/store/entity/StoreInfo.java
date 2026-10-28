package com.grainwarehouse.store.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Thông tin cửa hàng in ở đầu / cuối phiếu (BR-PRT-01). Đúng một dòng (id = 1, CHECK ck_store_info_single_row),
 * giống CashBook. Là cấu hình nên sửa đè được, không cần lịch sử.
 */
@Getter
@Entity
@Table(name = "store_info")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreInfo {

    public static final short ID = 1;

    @Id
    private Short id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 500)
    private String address;

    @Column(length = 50)
    private String phone;

    @Column(name = "receipt_footer", length = 300)
    private String receiptFooter;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static StoreInfo create() {
        StoreInfo info = new StoreInfo();
        info.id = ID;
        return info;
    }

    public void update(String name, String address, String phone, String receiptFooter) {
        this.name = name;
        this.address = address;
        this.phone = phone;
        this.receiptFooter = receiptFooter;
    }
}
