# Nhà cung cấp

[← Mục lục kho bài học](../README.md)

## Module: Nhà cung cấp (2026-10-16 → 2026-10-17)

### Backend

- **Khóa chính kép + `@EmbeddedId` / `@MapsId`** — `product_supplier` không có cột `id`, khóa chính là `(supplier_id, product_id)`. Gỡ "Cám heo con 25kg" khỏi NCC001 rồi gán lại vẫn chỉ có 1 dòng (đổi `active`). `@MapsId("productId")` cho biết cột `product_id` vừa là một phần khóa vừa là FK tới `product`.
  _Khi nào dùng `id` riêng:_ khi dòng nối có vòng đời riêng hoặc bảng khác cần trỏ tới nó (ví dụ bảng giá theo hợp đồng).
- **Thứ tự cột trong index ghép** — index `(supplier_id, product_id)` phục vụ được "NCC X có SP nào" nhưng **không** phục vụ "SP Y có NCC nào", vì `product_id` đứng sau. Cần thêm index riêng `idx_product_supplier_product`.
- **Flush của Hibernate** — `setActive(false)` chỉ đổi trong bộ nhớ; Hibernate ghi xuống DB khi commit hoặc trước một truy vấn JPA. Test `@Transactional` rollback nên không commit, `JdbcTemplate` đọc thẳng DB vẫn thấy giá trị cũ → phải `entityManager.flush()` trước khi đọc. Sẽ gặp lại khi trộn câu `UPDATE` viết tay (trừ tồn) với JPA.
- **`@EntityGraph` chống N+1** — danh sách 20 liên kết mà không JOIN sẵn `product`/`supplier` sẽ thành 1 + 20 câu SQL.
- **Phụ thuộc giữa module ≠ vị trí URL** — `GET /api/products/{id}/suppliers` đặt code trong module `supplier`, vì `supplier` đã phụ thuộc `product` (kiểm tra SP khi gán). Đặt ở `product` sẽ thành phụ thuộc vòng.
- **Một quy tắc, một chỗ kiểm tra** — gán/gỡ từ phía SP gọi lại API của NCC, không viết endpoint thứ hai → BR-SUP-05/06 chỉ được kiểm tra ở `SupplierService`.
- **Mã lỗi theo contract** — vi phạm nghiệp vụ (gán SP đã ngừng kinh doanh) là 409 `BUSINESS_RULE_VIOLATION`, không phải 400: dữ liệu gửi lên đúng định dạng, chỉ trạng thái hệ thống không cho phép.

### Frontend

- **Tìm kiếm phía server → `filterOption: false`** — gợi ý SP đã được backend lọc; nếu để `Select` lọc thêm trên trình duyệt có thể ẩn mất kết quả đúng. Ngược lại, danh sách NCC tải sẵn (≤ 100) thì lọc trên trình duyệt (`optionFilterProp: 'label'`).
- **Debounce** — chờ 400 ms sau phím cuối mới gọi API, tránh 1 request mỗi phím.
- **`key` để reset state** — `<SupplierProductsPanel key={supplier.id}>`: chuyển từ NCC A sang B, React tạo component mới, không giữ ô tìm kiếm/trang của A.
- **`useCallback` cho hàm tải dữ liệu** — `usePagedList` tải lại mỗi khi hàm đổi; không bọc `useCallback` thì mỗi lần render tạo hàm mới → gọi API liên tục.
- **Khả năng phát hiện chức năng (discoverability)** — chức năng gán SP ban đầu nằm cuối khung chi tiết nên người dùng tưởng chưa có; thêm mục riêng trong menu `⋯` mở thẳng khung gán.

### Công cụ / môi trường

- **Lỗi đỏ trong IDE ≠ lỗi build** — VS Code dùng trình biên dịch Eclipse (ECJ) riêng; ECJ mới + Lombok 1.18.34 báo nhầm "Flexible Constructor Bodies". Tin `mvn compile` trước, rồi mới nghi IDE lệch phiên bản. Sửa: nâng Lombok lên 1.18.42 + "Java: Clean Java Language Server Workspace".
- **Biên dịch tăng dần để lại bytecode cũ** — sửa tạm `WarehouseSearchCriteria` (bỏ `HasActive`) rồi khôi phục: Maven chỉ biên dịch lại file đổi, `WarehouseSpecifications.class` vẫn tham chiếu bản cũ → 82 test lỗi "Constructor threw exception" dù code đúng. `mvn clean verify` hết lỗi. Thấy lỗi khởi tạo bean khó hiểu sau khi đổi kiểu/chữ ký → thử `clean` trước khi sửa code.
- **`mvn clean` lỗi trên Windows** — thư mục `target/` bị IDE/app đang chạy khóa; chạy `mvn verify` không `clean`, hoặc tắt tiến trình đang giữ file.

### Câu hỏi ôn tập (để cuối dự án)

1. Nếu `product_supplier` dùng `id` tự tăng và **không** có UNIQUE `(supplier_id, product_id)`, hai người bấm "Gán" cùng lúc thì sao? Thiết kế hiện tại chặn ở đâu?
2. Vì sao truy vấn "SP Y do NCC nào cung cấp" không dùng được index của khóa chính?
3. Vì sao test `shouldSoftUnlink` fail lần đầu dù code service đúng?

## Thay đổi: NCC bỏ "người liên hệ", thêm "mã số thuế" (2026-10-24, migration V15)

- **Đổi nghĩa một cột = thêm cột mới + xóa cột cũ, không `RENAME`.** `ALTER TABLE ... RENAME COLUMN contact_name TO tax_code` chỉ đổi tên, dữ liệu giữ nguyên. Khi đó "Chị Lan" sẽ thành mã số thuế: dữ liệu sai, và còn vi phạm CHECK mới nên migration fail ngay. RENAME chỉ dùng khi **ý nghĩa giữ nguyên**, chỉ tên cột đổi.
- **Migration xóa dữ liệu không quay lại được.** Flyway chạy V15 ngay lúc backend khởi động trên server, nên `DROP COLUMN contact_name` xóa luôn tên người liên hệ trên production. Muốn quay lại thì chỉ còn cách khôi phục từ bản backup. Vì vậy phải **backup DB trước khi deploy** (docs/maintenance/backup-restore.md). Và không "sửa" lại V15 sau khi đã chạy: Flyway lưu checksum, sửa file cũ thì validate thất bại. Muốn đổi gì nữa thì viết V16.
- **Unique index một phần (partial index).** `CREATE UNIQUE INDEX ... ON supplier (tax_code) WHERE tax_code IS NOT NULL`: MST có nhập thì không được trùng, còn nhiều NCC chưa có MST vẫn hợp lệ. (Trong PostgreSQL, UNIQUE thường cũng coi các NULL là khác nhau; viết `WHERE` để ý đồ rõ ràng và index nhỏ hơn.) Đây là phiên bản "tùy chọn nhưng duy nhất" của `uk_customer_phone`.
- **Exception mang theo field.** `DuplicateResourceException` có thêm `field` (tùy chọn). API 409 vẫn trả như cũ, nhưng khi nhập Excel, lỗi "MST đã thuộc về NCC X" được gắn vào đúng cột "Mã số thuế" thay vì cột "Tên". Tương tự, `ImportDefinition.duplicateKeys()` trả về một map field → khóa, để mỗi file so trùng **từng** field riêng (tên và MST).
- **Đổi cấu trúc mẫu Excel thì tăng phiên bản** (`SUPPLIER_v1` → `v2`). Ai đang giữ file mẫu cũ sẽ nhận lỗi "không phải mẫu nhập", thay vì cột MST bị đọc nhầm thành "người liên hệ".
