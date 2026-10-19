# LESSONS — Bài học tích lũy

Ghi dồn sau mỗi module, ôn tập ở cuối dự án. Mỗi mục: **khái niệm → ví dụ trong dự án → vì sao quan trọng**.
`NOTES.md` vẫn là sổ tay riêng của bạn; file này do AI ghi để không bỏ sót kiến thức.

---

## Module: Bộ lọc dùng chung + rà soát UI/UX danh sách (2026-10-19)

### Backend

- **Specification dùng chung, tên cột do code quyết định** — `CommonSpecifications.keywordContains(keyword, "code", "name")` dùng lại được cho SP, NCC, Kho. Tên thuộc tính được **viết cứng trong service**, không lấy từ request: nếu cho client gửi `?filter[field]=...` tùy ý, người dùng có thể lọc cột không có index (chậm) hoặc cột không nên lộ.
- **Lọc trong SQL, không lọc trên trình duyệt** — lọc 20 dòng đang xem thì `totalElements` và số trang sai. Ví dụ: 45 NCC, lọc "Ngừng hoạt động" trên trang 1 chỉ thấy 2 dòng, nhưng thực tế có 7 NCC ngừng nằm rải ở trang 2, 3.

### Frontend

- **Form lọc "chung" = khai báo cấu hình, không suy ra từ entity** — kiểu TypeScript bị xóa khi chạy, nên không thể "ném object `Supplier` vào" rồi tự sinh ô lọc. `FilterBar` nhận mảng `fields` (`keyword` / `select` / `status`); màn hình mới chỉ khai báo 2–3 dòng. Chỉ khai báo những tham số API thực sự hỗ trợ.
- **`type` thay vì `interface` cho bộ lọc** — `type SupplierFilters = {...}` gán được vào `Record<string, ...>`, còn `interface` thì không (interface có thể được mở rộng sau nên TS không cho ngầm định index signature).
- **Lùi trang khi trang hiện tại rỗng** — đang lọc "Đang kinh doanh", trang 3 còn 1 SP, ngừng kinh doanh SP đó → tải lại trang 3 rỗng. `fallbackPageIndex` đưa về trang 2 thay vì báo "không có kết quả".
- **Chống mất dữ liệu form** — `maskClosable={false}` + hỏi lại khi `form.isFieldsTouched()`. Form chưa sửa thì đóng ngay: hỏi thừa làm người dùng quen bấm "Đồng ý" mà không đọc.
- **Một bản ghi – một khung** — gộp khung gán/gỡ riêng vào tab trong `DetailDrawer`; menu `⋯` mở thẳng tab đó. Tránh hai drawer làm cùng một việc.
- **Accessibility của mã bấm được** — `Typography.Link` không có `href` là thẻ `<a>` không Tab tới được; đổi sang `Button type="link"` (thẻ `<button>`). `aria-label` của nút `⋯` kèm mã bản ghi để trình đọc màn hình phân biệt các dòng.

### Câu hỏi ôn tập (để cuối dự án)

1. Vì sao không cho client gửi tên cột tùy ý để lọc, dù làm vậy "chung" hơn?
2. "Mở lại" gửi PUT với dữ liệu của dòng trong bảng. Nếu người khác vừa sửa tên NCC đó, chuyện gì xảy ra? Cần gì để phát hiện (gợi ý: optimistic locking / `version`)?

---

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
- **`mvn clean` lỗi trên Windows** — thư mục `target/` bị IDE/app đang chạy khóa; chạy `mvn verify` không `clean`, hoặc tắt tiến trình đang giữ file.

### Câu hỏi ôn tập (để cuối dự án)

1. Nếu `product_supplier` dùng `id` tự tăng và **không** có UNIQUE `(supplier_id, product_id)`, hai người bấm "Gán" cùng lúc thì sao? Thiết kế hiện tại chặn ở đâu?
2. Vì sao truy vấn "SP Y do NCC nào cung cấp" không dùng được index của khóa chính?
3. Vì sao test `shouldSoftUnlink` fail lần đầu dù code service đúng?

---

## Các module trước (UI polish, 2026-10-16)

### Câu hỏi ôn tập

1. Skeleton loading khác spinner thế nào? Vì sao skeleton làm người dùng cảm thấy trang tải nhanh hơn?
2. Vì sao chỉ hiện thông báo thành công **sau khi** API trả `success: true`, không hiện ngay lúc bấm nút?
