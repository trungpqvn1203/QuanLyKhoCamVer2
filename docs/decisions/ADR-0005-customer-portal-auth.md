# ADR-0005: Đăng nhập cho khách hàng (app khách tự đặt hàng)

## Trạng thái

✅ **Đã chấp nhận — 2026-11-03** (duyệt toàn bộ phương án B, schema V27, thời hạn phiên khách 30 / 90 ngày).

Đã chốt nghiệp vụ (2026-11-03): khách chỉ thấy giá **sau khi cửa hàng xác nhận** đơn; chưa cho khách xem công nợ; khách được **tự hủy** đơn còn Chờ xác nhận; cửa hàng tạo tài khoản cho khách; làm và thử trên máy trước, HTTPS công khai làm sau (xem mục 7).

## Bối cảnh

- Một người vừa quản kho vừa nghe điện thoại đặt hàng, hằng ngày nhận rất nhiều đơn. Mục tiêu: khách tự đặt trên điện thoại, đơn vào thẳng danh sách **Chờ xác nhận** (BR-SAL-16..22, V25) với kênh `APP`.
- Nền tảng: PWA trong project React hiện tại, đường dẫn `/khach` (đã chốt; React Native để sau — xem mục 8).
- Hệ thống đăng nhập nhân viên (ADR-0003) có nhiều quy tắc chỉ yêu cầu `authenticated()`: `GET /api/customers/**`, `/api/sales-orders/**`, `/api/inventory/**`, `/api/reports/**`… Nếu khách có token mà backend nhân viên chấp nhận, **khách đọc được toàn bộ khách khác, phiếu bán, tồn kho, báo cáo**.
- `JwtDecoder` hiện tại kiểm **chữ ký + `iss` + thời hạn**, không kiểm vai trò. Token khách ký cùng khóa, cùng `iss` thì chỉ cần tự đặt vào cookie `GW_AT` (bằng curl, không cần trình duyệt) là qua được các quy tắc `authenticated()`.

## Phương án

| Phương án | Cách làm | Ưu | Nhược | Kết luận |
|---|---|---|---|---|
| A. Khách là `app_user` với vai trò `CUSTOMER` | Thêm vai trò, sửa mọi `authenticated()` thành danh sách vai trò nhân viên | Một bảng tài khoản, một luồng đăng nhập | Mọi quy tắc hiện có và sau này phải nhớ loại trừ `CUSTOMER`; quên một chỗ là lộ dữ liệu. `app_user` trộn người trong và ngoài cửa hàng | ❌ |
| **B. Vùng đăng nhập riêng** | Bảng tài khoản riêng, `SecurityFilterChain` riêng cho `/api/portal/**`, cookie riêng, token **ký khóa khác + `iss` khác** | Hai vùng không thể lẫn: token khách không bao giờ được backend nhân viên chấp nhận, kể cả khi ai đó quên quy tắc | Thêm một luồng đăng nhập (dùng lại gần hết code của ADR-0003) | ✅ **Đề xuất** |
| C. Nhà cung cấp danh tính ngoài (Keycloak, Firebase Auth, OTP SMS) | Khách đăng nhập qua dịch vụ ngoài | Có sẵn OTP, quên mật khẩu | Thêm dịch vụ / chi phí SMS; vượt MVP | ⏸ Khi khách tự đăng ký hàng loạt |

## Quyết định đề xuất (phương án B)

### 1. Hai vùng, không dùng chung gì về xác thực

| | Nhân viên (ADR-0003, giữ nguyên) | Khách (mới) |
|---|---|---|
| API | `/api/**` trừ `/api/portal/**` | **Chỉ** `/api/portal/**` |
| `SecurityFilterChain` | Chuỗi hiện có (`@Order` sau) | Chuỗi mới, `securityMatcher("/api/portal/**")`, `@Order` trước |
| Bảng tài khoản | `app_user` | `customer_account` (V27) |
| Cookie access | `GW_AT`, `Path=/api` | `GW_CAT`, `Path=/api/portal` |
| Cookie refresh | `GW_RT`, `Path=/api/auth` | `GW_CRT`, `Path=/api/portal/auth` |
| `iss` của JWT | `grain-warehouse` | `grain-warehouse-portal` |
| Khóa ký | `JWT_SECRET` | **Khóa dẫn xuất** `HMAC-SHA256(JWT_SECRET, "portal-v1")` — khóa khác thật sự, không thêm biến môi trường |
| `sub` | id `app_user` | id `customer_account`; claim `customerId` |

- **Hai lớp tách biệt:** khóa khác → decoder nhân viên không xác minh được chữ ký token khách (và ngược lại); `iss` khác → kể cả khi sau này ai đó lỡ dùng chung khóa, validator `iss` vẫn chặn. Test: token khách đặt vào `GW_AT` gọi `GET /api/customers` → 401; token nhân viên gọi `/api/portal/orders` → 401.
- Cookie `GW_AT` (Path=`/api`) vẫn được trình duyệt gửi tới `/api/portal/**`, nhưng chuỗi khách **chỉ đọc `GW_CAT`** → không dính.
- CSRF: dùng chung cơ chế double-submit (`XSRF-TOKEN`, `GET /api/auth/csrf`) — cookie này không mang quyền gì.
- Thời hạn: access **15 phút** như ADR-0003; refresh **30 ngày không dùng / 90 ngày tối đa** (nhân viên: 8 giờ / 24 giờ). Khách mở app vài ngày một lần, bắt đăng nhập lại mỗi ngày thì khách bỏ app. Đánh đổi: điện thoại bị mất thì phiên sống lâu hơn → cửa hàng có nút "Khóa tài khoản" (thu hồi mọi refresh token ngay; access token còn tối đa 15 phút).

### 2. Dữ liệu khách chỉ lấy từ token

- Mọi API `/api/portal/**` lấy `customerId` **từ token**, không bao giờ từ URL / body. `GET /api/portal/orders/{id}` của khách khác → **404** (không phải 403, để không lộ phiếu đó tồn tại).
- Không có API portal nào trả: phiếu khách khác, tồn kho, giá vốn, công nợ, thông tin nội bộ (kho xuất, người lập / xác nhận).

### 3. Đăng nhập & bảo vệ tài khoản

- Tên đăng nhập = **SĐT của khách** (`customer.phone`, đã duy nhất, chuẩn hóa bằng `PhoneNumbers.normalize`). Mật khẩu BCrypt, `PasswordPolicy` như nhân viên (≥ 8 ký tự, ≤ 72 byte).
- **Cửa hàng tạo tài khoản** và đặt mật khẩu ban đầu → `must_change_password = true` → khách phải đổi mật khẩu trước khi đặt hàng (backend chặn API đặt hàng, không chỉ ẩn nút).
- **Sai mật khẩu 5 lần liên tiếp → khóa 15 phút** (`failed_login_count`, `locked_until`); đúng thì đếm lại từ 0. Thông báo lỗi không phân biệt "sai SĐT" và "sai mật khẩu".
- Khách bị ngừng hoạt động (`customer.active = false`) hoặc tài khoản bị khóa → không đăng nhập / gia hạn được.

### 4. Schema (migration V27)

**`customer_account`**

| Cột | Ghi chú |
|---|---|
| `id` PK | |
| `customer_id` FK → customer, **UNIQUE** | Mỗi khách tối đa một tài khoản |
| `password_hash` VARCHAR(100) | `{bcrypt}…` như `app_user` |
| `must_change_password` BOOLEAN NOT NULL DEFAULT TRUE | |
| `active` BOOLEAN NOT NULL DEFAULT TRUE | Cửa hàng khóa / mở |
| `failed_login_count` INT NOT NULL DEFAULT 0, CHECK ≥ 0 | |
| `locked_until` TIMESTAMPTZ | Khóa tạm do sai mật khẩu |
| `last_login_at`, `created_by` VARCHAR(50), `created_at`, `updated_at` | |

**`customer_refresh_token`**: cấu trúc y hệt `refresh_token` (V14) — `family_id`, `token_hash` SHA-256 UNIQUE, `expires_at`, `family_expires_at`, `replaced_by_id/at`, `revoked_at/reason`, `user_agent` — FK `customer_account_id`. Bảng riêng: FK khác bảng, và thu hồi phiên khách không bao giờ đụng phiên nhân viên.

**Đơn của khách: không thêm bảng.** `POST /api/portal/orders` tạo đúng phiếu bán `PENDING` (V25) với `order_channel = 'APP'`, `created_by = 'app:' + mã khách`. Bỏ chặn `APP` ở `SalesOrderService` **chỉ cho đường portal**; giao diện kho vẫn không chọn được `APP`.

### 5. API

**Khách** (`/api/portal/**`):

| Endpoint | Ghi chú |
|---|---|
| `POST /api/portal/auth/login` `{ phone, password }` · `POST …/refresh` · `POST …/logout` | Công khai (cần CSRF) |
| `GET /api/portal/me` · `PUT /api/portal/me/password` | Tên, mã khách, SĐT, `mustChangePassword` |
| `GET /api/portal/products` | SP **đang kinh doanh**: mã, tên, ĐVT, danh mục. **Không** giá, **không** tồn |
| `POST /api/portal/orders` `{ requestedDeliveryDate?, note?, lines: [{ productId, quantity }] }` | Phiếu `PENDING`, kênh `APP` |
| `GET /api/portal/orders` (phân trang, lọc trạng thái) · `GET /api/portal/orders/{id}` | Chỉ đơn của mình |
| `PUT /api/portal/orders/{id}` | Sửa khi còn Chờ xác nhận (BR-SAL-18) |
| `POST /api/portal/orders/{id}/cancel` `{ note? }` | Khi còn Chờ xác nhận; lý do ghi tự động "Khách hủy qua app" (+ ghi chú) — thỏa BR-SAL-21 |

Chi tiết đơn khách thấy: mã, trạng thái hiển thị, ngày đặt, ngày muốn giao, ghi chú, **phần khách đặt**; từ khi cửa hàng xác nhận thêm **dòng chốt: SP, SL, đơn giá, thành tiền, tổng** (giá mới nhất, kể cả sau điều chỉnh giá BR-SAL-23); đơn bị hủy kèm lý do. Trạng thái hiển thị: `PENDING` "Chờ cửa hàng xác nhận" · `DRAFT` "Đã nhận đơn" · `COMPLETED` "Đã giao" · `CANCELLED` "Đã hủy" · `REVERSED` "Đã hủy giao".

**Cửa hàng** (chuỗi nhân viên, quyền như ghi khách hàng: bán hàng + quản lý hệ thống):

| Endpoint | Ghi chú |
|---|---|
| `GET /api/customers/{id}/portal-account` | Có tài khoản chưa, đang hoạt động / bị khóa tạm, lần đăng nhập cuối |
| `POST /api/customers/{id}/portal-account` `{ password }` | Tạo; khách phải đổi mật khẩu lần đầu |
| `PUT /api/customers/{id}/portal-account/password` `{ password }` | Đặt lại → thu hồi mọi phiên của khách, bắt đổi lần đầu |
| `POST …/portal-account/lock` · `…/unlock` | Khóa → thu hồi mọi phiên; mở → xóa đếm sai mật khẩu |

### 6. Frontend

- `router.tsx`: route `/khach/*` **ngoài** nhánh `/` (nhánh đó bọc `RequireAuth` phiên nhân viên + menu quản trị), tải lười (`lazy`). Code trong `features/customer-portal/`.
- Bố cục điện thoại: thanh dưới **Đặt hàng · Đơn của tôi · Tài khoản**; đặt lại đơn cũ một chạm (điền sẵn từ phần khách đặt).
- `public/manifest-khach.json` để "Thêm vào màn hình chính". **Không thêm thư viện** (không service worker / thông báo đẩy ở bước này).
- Màn hình Khách hàng (quản trị): mục "Tài khoản app" — tạo / đặt lại mật khẩu / khóa.

### 7. Triển khai (ngoài phạm vi ADR này)

- Cookie `Secure` + PWA cài đặt được **bắt buộc HTTPS**. Trên Azure: tên miền miễn phí `*.cloudapp.azure.com` + chứng chỉ Let's Encrypt trên Nginx — làm thành việc riêng ở `docs/maintenance/`. Trước đó chỉ thử trên máy (`COOKIE_SECURE=false` như dev hiện tại).
- Đăng nhập khách mở ra Internet → nên giới hạn tốc độ theo IP ở Nginx (`limit_req`) khi triển khai thật; ADR này chỉ có khóa theo tài khoản.

### 8. Khi có app native (React Native) — việc sẽ phải làm thêm

- App native không dùng cookie thuận tiện như trình duyệt: token giữ trong Keychain / Keystore, gửi header `Authorization: Bearer`. Khi đó thêm chế độ "bearer header" cho chuỗi portal (header-only, **không** CSRF vì trình duyệt không tự gửi header) — tách endpoint đăng nhập trả token trong body cho app.
- API `/api/portal/**` và nghiệp vụ giữ nguyên.

## Kế hoạch kiểm thử

- Ma trận: token khách → mọi API nhân viên 401; token nhân viên → `/api/portal/**` 401; không token → 401; khách A đọc / sửa / hủy đơn khách B → 404.
- Đăng nhập: đúng / sai / 5 lần sai → khóa 15 phút / tài khoản bị khóa / khách ngừng hoạt động; rotation + phát hiện dùng lại refresh token như ADR-0003.
- `must_change_password` → đặt hàng 403 cho đến khi đổi mật khẩu.
- Đặt hàng → phiếu `PENDING` kênh `APP` hiện ở danh sách cửa hàng; khách thấy giá chỉ sau khi xác nhận; tự hủy chỉ khi Chờ xác nhận.
- CSRF bắt buộc với mọi request ghi của portal.

## Còn mở / làm sau

- Khách tự đăng ký + OTP SMS (phương án C), quên mật khẩu tự phục vụ (hiện: gọi cửa hàng đặt lại).
- Thông báo đẩy khi đơn được xác nhận (cần service worker — hỏi trước khi thêm thư viện).
- Khách xem công nợ.
