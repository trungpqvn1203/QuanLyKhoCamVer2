package com.grainwarehouse.product.service;

import com.grainwarehouse.product.entity.Product;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Gán NCC ngay khi thêm / sửa SP (BR-PRD-10). Module product chỉ khai báo interface này; module supplier cài đặt.
 * Nhờ vậy product không import supplier — supplier vốn đã phụ thuộc product (ProductSupplier trỏ tới Product),
 * import ngược lại sẽ thành phụ thuộc vòng giữa hai module (đảo chiều phụ thuộc — Dependency Inversion).
 */
public interface ProductSupplierLinks {

    /** NCC dùng được trong file Excel. */
    record SupplierOption(String code, String name) {
    }

    /** NCC đang hoạt động, sắp theo mã — dropdown cột "Nhà cung cấp" của file mẫu. Tự mở transaction chỉ-đọc. */
    List<SupplierOption> activeSuppliers();

    /** Mã NCC (không phân biệt hoa thường) → id; đọc file Excel. Tự mở transaction chỉ-đọc. */
    Optional<Long> findSupplierIdByCode(String code);

    /**
     * Kiểm tra trước khi lưu, KHÔNG ghi gì: NCC phải tồn tại (404). Liên kết sẽ tạo mới / bật lại thì NCC phải đang
     * hoạt động và SP còn kinh doanh (409, BR-SUP-06). SP chưa lưu (id null) = mọi NCC đều là liên kết mới.
     */
    void validate(Product product, Set<Long> supplierIds);

    /**
     * Đưa liên kết đang dùng của SP về đúng tập supplierIds (BR-SUP-05): chọn thêm → tạo / bật lại dòng,
     * bỏ chọn → active=false. Phải gọi trong transaction của người gọi, sau khi SP đã có id.
     */
    void sync(Product product, Set<Long> supplierIds);
}
