# Bộ lọc dùng chung và giao diện danh sách

[← Mục lục kho bài học](../README.md)

## Các module trước (UI polish, 2026-10-16)

### Câu hỏi ôn tập

1. Skeleton loading khác spinner thế nào? Vì sao skeleton làm người dùng cảm thấy trang tải nhanh hơn?
2. Vì sao chỉ hiện thông báo thành công **sau khi** API trả `success: true`, không hiện ngay lúc bấm nút?

## Module: Bộ lọc dùng chung + rà soát UI/UX danh sách (2026-10-19)

### Backend

- **Specification dùng chung, tên cột do code quyết định** — `CommonSpecifications.keywordContains(keyword, "code", "name")` dùng lại được cho SP, NCC, Kho. Tên thuộc tính được **viết cứng trong service**, không lấy từ request: nếu cho client gửi `?filter[field]=...` tùy ý, người dùng có thể lọc cột không có index (chậm) hoặc cột không nên lộ.
- **Lọc trong SQL, không lọc trên trình duyệt** — lọc 20 dòng đang xem thì `totalElements` và số trang sai. Ví dụ: 45 NCC, lọc "Ngừng hoạt động" trên trang 1 chỉ thấy 2 dòng, nhưng thực tế có 7 NCC ngừng nằm rải ở trang 2, 3.
- **Tìm không dấu bằng `unaccent` (V5)** — gõ "cam heo" ra "Cám heo con 25kg". Hai vế (cột và từ khóa) đều chuẩn hóa **trong SQL** bằng cùng hàm `lower(immutable_unaccent(...))`, tránh lệch giữa cách Java và PostgreSQL bỏ dấu.
- **`IMMUTABLE` vs `STABLE`** — PostgreSQL chỉ cho đánh index trên biểu thức dùng hàm `IMMUTABLE` (cùng đầu vào luôn cùng kết quả). `unaccent()` là `STABLE` vì từ điển có thể đổi → bọc lại, chỉ định rõ từ điển. `LIKE '%cam%'` (có `%` ở đầu) không dùng được B-tree; khi cần nhanh phải dùng `pg_trgm` + GIN.
- **Tiêu chí lọc là record, bind bằng `@ModelAttribute`** — `GET /api/suppliers?keyword=cam&active=true` → `SupplierSearchCriteria(keyword, active)`. Thêm tiêu chí = thêm field vào record + một dòng ở `XxxSpecifications.from`. Vẫn giữ GET: tìm kiếm là đọc dữ liệu; `POST /get-data` biến API thành RPC, mất F5/bookmark/cache.
- **Interface nhỏ thay cho base class (composition over inheritance)** — record không `extends` được; `BaseSearchRequest { keyword, active }` buộc mọi module mang `active` kể cả lịch sử giao dịch (không có trạng thái). `record SupplierSearchCriteria(...) implements HasKeyword, HasActive` chỉ nhận đúng phần mình có, và `CommonSpecifications.hasActive(criteria)` nhận thẳng interface.
- **Test giữ quy ước (convention test)** — `SearchConventionTest` quét classpath bằng `ClassPathScanningCandidateComponentProvider` của Spring (không thêm ArchUnit): `*SearchCriteria` phải là record, có `XxxSpecifications.from(...)`. Đã thử cố ý bỏ `HasActive` khỏi `WarehouseSearchCriteria` → test fail đúng chỗ. Kèm một test "phải tìm thấy 3 module" để tránh pass rỗng khi quét sai package.
- **Lọc ngày trên cột `Instant`** — "đến 15/10" phải gồm cả 23:59 ngày 15/10 giờ VN; 00:00 ngày 15/10 giờ VN là 17:00 ngày 14/10 UTC. `instantWithinDates` dùng `>= đầu ngày from` và `< đầu ngày (to + 1)`, không dùng `BETWEEN`.
- **Không đổi envelope chỉ vì "chuẩn khác"** — đề xuất `ApiResponse {statusCode, ...}` và `PagedResponse {pageIndex từ 1, ...}` trùng chức năng với cái đã có; đổi thì phải sửa FE, contract và 78 test mà không thêm giá trị. `statusCode` trong body lặp lại HTTP status.

### Frontend

- **Form lọc "chung" = khai báo cấu hình, không suy ra từ entity** — kiểu TypeScript bị xóa khi chạy, nên không thể "ném object `Supplier` vào" rồi tự sinh ô lọc. `FilterBar` nhận mảng `fields` (`keyword` / `select` / `status`); màn hình mới chỉ khai báo 2–3 dòng. Chỉ khai báo những tham số API thực sự hỗ trợ.
- **`type` thay vì `interface` cho bộ lọc** — `type SupplierFilters = {...}` gán được vào `Record<string, ...>`, còn `interface` thì không (interface có thể được mở rộng sau nên TS không cho ngầm định index signature).
- **Lùi trang khi trang hiện tại rỗng** — đang lọc "Đang kinh doanh", trang 3 còn 1 SP, ngừng kinh doanh SP đó → tải lại trang 3 rỗng. `fallbackPageIndex` đưa về trang 2 thay vì báo "không có kết quả".
- **Chống mất dữ liệu form** — `maskClosable={false}` + hỏi lại khi `form.isFieldsTouched()`. Form chưa sửa thì đóng ngay: hỏi thừa làm người dùng quen bấm "Đồng ý" mà không đọc.
- **Một bản ghi – một khung** — gộp khung gán/gỡ riêng vào tab trong `DetailDrawer`; menu `⋯` mở thẳng tab đó. Tránh hai drawer làm cùng một việc.
- **Accessibility của mã bấm được** — `Typography.Link` không có `href` là thẻ `<a>` không Tab tới được; đổi sang `Button type="link"` (thẻ `<button>`). `aria-label` của nút `⋯` kèm mã bản ghi để trình đọc màn hình phân biệt các dòng.

### Câu hỏi ôn tập (để cuối dự án)

1. Vì sao không cho client gửi tên cột tùy ý để lọc, dù làm vậy "chung" hơn?
2. Vì sao không tạo được index trên `lower(unaccent(name))` nhưng tạo được trên `lower(immutable_unaccent(name))`?
3. "Mở lại" gửi PUT với dữ liệu của dòng trong bảng. Nếu người khác vừa sửa tên NCC đó, chuyện gì xảy ra? Cần gì để phát hiện (gợi ý: optimistic locking / `version`)?
