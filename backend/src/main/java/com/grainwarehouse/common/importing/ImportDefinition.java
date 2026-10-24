package com.grainwarehouse.common.importing;

import java.util.List;
import java.util.Map;

/**
 * Mô tả cách nhập một loại dữ liệu từ Excel. Mỗi module (sản phẩm, khách hàng...) cài đặt một bean;
 * ExcelImportService lo phần chung: sinh file mẫu, đọc file, gom lỗi, transaction.
 *
 * @param <R> request tạo mới của module (CreateProductRequest...) — dùng lại Bean Validation của form nhập tay
 * @param <E> entity đã qua kiểm tra nghiệp vụ, chưa lưu
 */
public interface ImportDefinition<R, E> {

    /** Ghi vào sheet ẩn của file mẫu; file upload phải mang đúng giá trị này (chặn nhầm mẫu / mẫu cũ). */
    String templateId();

    /** Tên loại dữ liệu, chữ thường: "sản phẩm". */
    String entityLabel();

    /** Tên file mẫu khi tải về (ASCII để mọi trình duyệt hiển thị đúng). */
    String fileName();

    /** Có thể đọc DB (ví dụ dropdown danh mục) nên gọi lại mỗi lần sinh mẫu / đọc file. */
    List<ImportColumn> columns();

    /** Một dòng ví dụ in ở sheet "Hướng dẫn", theo đúng thứ tự columns(). */
    List<String> example();

    /** Ép kiểu các ô thành request. Ô sai kiểu → row.error(...), field đó để null. */
    R parse(ImportRow row);

    /**
     * Khóa so trùng giữa các dòng trong cùng file: field → khóa đã chuẩn hóa. Mỗi field so riêng
     * (NCC: tên và MST). Khóa null = dòng này không cần so field đó (ví dụ MST để trống).
     * Trùng với DB do prepare() phát hiện (DuplicateResourceException mang field).
     */
    Map<String, String> duplicateKeys(R request);

    /**
     * Chạy đúng các kiểm tra nghiệp vụ của chức năng tạo tay (trùng DB, danh mục ngừng dùng...) và dựng entity chưa lưu.
     * Vi phạm → ném exception như khi tạo tay; ExcelImportService đổi thành lỗi của dòng.
     */
    E prepare(R request);

    void saveAll(List<E> entities);
}
