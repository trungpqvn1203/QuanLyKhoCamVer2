# ADR-0002: Thư viện UI và cấu trúc giao diện Frontend

## Trạng thái

✅ Đã chấp nhận — 2026-10-14

## Bối cảnh

Frontend sẽ có nhiều màn hình cùng dạng (danh sách sản phẩm, kho, chứng từ, lịch sử giao dịch; form nhập/xuất/điều chuyển). Cần:
1. Giao diện thống nhất và hiện đại — sửa một button/table ở **một chỗ**, mọi màn hình cùng đổi.
2. Ưu tiên học **luồng dữ liệu FE ↔ BE** (request, envelope `ApiResponse`, xử lý lỗi), không dồn thời gian vào tự dựng component.

## Quyết định

- Dùng **Ant Design 6.x** (hỗ trợ React 19 trực tiếp, peer `react >= 18`).
- Theme hiện đại cấu hình **tập trung** qua `ConfigProvider` (màu chủ đạo, bo góc, font) — một file duy nhất.
- Tầng `api/` **tự viết bằng `fetch`**: đọc envelope, kiểm tra `success`, map `error.details[]` vào ô form. Không dùng Axios, React Query hay client tự sinh trong MVP.
- Chia giao diện 4 tầng:

| Tầng | Vị trí | Nội dung |
|---|---|---|
| 1. Theme / tokens | `src/theme/` | Cấu hình `ConfigProvider` |
| 2. UI dùng chung | `src/components/ui/` | Chỉ bọc mẫu lặp riêng của dự án, ví dụ `DataTable` nhận `data.pagination` theo contract-spec |
| 3. Layout | `src/components/layout/` | `AppLayout` (sidebar + header), `PageHeader` |
| 4. Màn hình nghiệp vụ | `src/features/<module>/` | Ghép tầng 1–3, chỉ chứa logic nghiệp vụ |

**Luật:** tầng 4 không dùng thẻ `<button>`/`<table>` thô, không viết màu/kích thước cứng.

### Bổ sung (2026-10-16)

- **`react-router-dom` 7.x** — mỗi màn hình có URL riêng (`/products`, `/warehouses`), F5 và nút Back hoạt động. Phương án không thêm thư viện (đổi màn hình bằng state) bị loại vì mất URL.
- **`@ant-design/icons`** — bộ icon chính thức đi kèm Ant Design, chỉ dùng hiển thị.
- `src/hooks/` chứa hook dùng chung (ví dụ `usePagedList` tải danh sách phân trang).
- `FormModal` (tầng 2) gom xử lý lỗi form: `VALIDATION_FAILED` → lỗi dưới từng ô; lỗi khác → `Alert` trong modal.

### Bổ sung (2026-10-16, đợt 2): khai thác thêm component có sẵn của Ant Design, **không thêm thư viện**.

- `RowActions` (tầng 2): một nút `⋯` mở menu (Xem chi tiết / Sửa / Ngừng). Thao tác ngừng hỏi lại bằng `modal.confirm`, vì `Popconfirm` biến mất khi menu đóng.
- `DetailDrawer` (tầng 2): xem chi tiết bằng `Drawer` + `Descriptions`, dùng luôn dữ liệu của dòng trong bảng, không gọi thêm API. Bấm vào mã ở cột đầu hoặc chọn "Xem chi tiết" để mở.
- Thông báo: thao tác **thành công** dùng `notification`, ghi rõ mã và tên bản ghi. **Lỗi** dùng `message`.
- `DataTable`: lần tải đầu hiện `Skeleton` theo đúng cột, các lần sau giữ dữ liệu cũ kèm vòng xoay.
- `src/utils/format.ts`: định dạng ngày giờ bằng `Intl`, cố định múi giờ `Asia/Ho_Chi_Minh`, không thêm thư viện.
- `DetailDrawer` nhận thêm `size` và `children` (2026-10-16): đặt bảng con dưới phần thông tin, ví dụ danh sách SP của nhà cung cấp kèm ô gán/gỡ. Chọn cách này thay vì trang chi tiết riêng (`/suppliers/:id`) vì MVP chỉ có một bảng con.
- `RowActions` nhận thêm `extraActions` (2026-10-17): mục riêng của từng màn hình chèn sau "Sửa", ví dụ "Sản phẩm cung cấp" (NCC) / "Nhà cung cấp" (SP) mở thẳng khung gán/gỡ — chức năng nằm sâu trong khung chi tiết thì người dùng khó phát hiện.

## Phương án thay thế đã xem xét

| Phương án | Lý do không chọn |
|---|---|
| Tự viết component + CSS | Tốn 1–2 tuần cho Table/Form/Modal, lệch mục tiêu học backend & luồng FE ↔ BE |
| MUI | Phong cách Material, ít component chuyên cho giao diện quản trị hơn Ant Design; không có lợi thế rõ ràng |
| shadcn/ui | Kéo theo Tailwind, Radix, TanStack Table, react-hook-form, zod — nhiều kiến thức frontend mới, làm loãng mục tiêu |

## Đánh đổi

1. **Bundle lớn hơn** so với tự viết — chấp nhận được với ứng dụng quản trị nội bộ.
2. **`Form` của Ant Design tự quản lý state** → có thể che bước "gom dữ liệu → gửi request". Khắc phục: tự viết `onFinish(values)` gọi tầng `api/` và tự map lỗi validation vào form.
3. **Giao diện mang dấu ấn Ant Design** — giảm bớt bằng theme tùy chỉnh.

## Khi nào xem xét lại

- Khi muốn học sâu frontend hoặc cần giao diện khác biệt hoàn toàn → cân nhắc shadcn/ui.
- Khi số màn hình và nhu cầu cache dữ liệu tăng → cân nhắc React Query (sau khi đã hiểu luồng `fetch` thủ công).
