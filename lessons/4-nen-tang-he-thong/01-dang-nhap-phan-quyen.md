# Đăng nhập & phân quyền

[← Mục lục kho bài học](../README.md)

## Module: Đăng nhập & phân quyền (2026-10-20, V14, ADR-0003)

### Spring Security hoạt động thế nào — đi theo một request

Ví dụ: chị Lan (Nhân viên bán hàng) bấm "Hoàn tất" phiếu PX000007 → `POST /api/sales-orders/7/complete`.

1. **Chuỗi filter (`SecurityFilterChain`)** — mọi request đi qua một dãy filter *trước* DispatcherServlet/controller. Spring Boot tự đăng ký chuỗi này; `SecurityConfig.securityFilterChain` quyết định có những filter nào.
2. **`CsrfFilter`** — request ghi phải có header `X-XSRF-TOKEN` bằng cookie `XSRF-TOKEN`. Thiếu → `AccessDeniedHandler` → 403. *Lý do tồn tại:* cookie đăng nhập được trình duyệt **tự gửi**, kể cả khi request do trang `kho-gia.vn` tạo ngầm. Trang đó không đọc được cookie của tên miền mình nên không đặt được header.
3. **`BearerTokenAuthenticationFilter`** — `CookieBearerTokenResolver` lấy JWT từ cookie `GW_AT` → `JwtAuthenticationProvider` → **`JwtDecoder`** kiểm chữ ký HS256, `exp`, `iss` (không truy vấn DB) → `JwtAuthenticationConverter` đổi claim `roles: ["SALES_STAFF"]` thành authority `ROLE_SALES_STAFF` → lưu vào **`SecurityContext`** (người đang gọi là ai, có quyền gì). Token hỏng/hết hạn → `AuthenticationEntryPoint` → 401.
4. **`AuthorizationFilter`** — so URL + method với `accessRules`: `POST /api/sales-orders/**` → `hasAnyRole(SALES, ADMIN)` → qua. Anh Hùng (Quản lý kho) gọi cùng URL → 403, **`SalesOrderService` không bao giờ chạy**.
5. Controller lấy người dùng bằng `@AuthenticationPrincipal Jwt jwt` (`jwt.getSubject()` = id).

**Đăng nhập** không có filter riêng: `AuthController` → `AuthenticationManager` → `DaoAuthenticationProvider` → `UserDetailsService` (đọc `app_user`) → `PasswordEncoder.matches` (BCrypt) → thành công thì `JwtEncoder` ký access token, `TokenService` sinh refresh token, rồi đặt cookie.

### Khái niệm

- **Access token vs refresh token** — access JWT sống 15 phút, xác minh chỉ bằng chữ ký nên nhanh, nhưng **không thu hồi được**. Refresh token sống tới 8 giờ không thao tác, mỗi lần dùng phải tra DB nên **thu hồi được**. Kết hợp: thiệt hại khi lộ access token giới hạn ở 15 phút; khóa tài khoản có hiệu lực trong ≤ 15 phút.
- **JWT chỉ được ký, không được mã hóa** — dán token vào jwt.io là đọc được `username`, `roles`. Chữ ký chỉ bảo đảm *không sửa được*. Không đặt dữ liệu nhạy cảm vào payload.
- **Cookie HttpOnly + Secure + SameSite=Strict** — HttpOnly: `document.cookie` không đọc được, nên XSS không lấy được token. Secure: chỉ gửi qua HTTPS (trình duyệt cho phép ngoại lệ `http://localhost`). SameSite=Strict: không gửi khi request đến từ trang khác. `Path=/api/auth` cho `GW_RT`: các API khác không bao giờ nhận refresh token.
- **Rotation + phát hiện dùng lại** — mỗi lần gia hạn, refresh token cũ bị đánh dấu "đã thay". Ví dụ kẻ gian chép được RT1: người thật gia hạn ra RT2; 1 giờ sau kẻ gian dùng RT1 → backend biết RT1 đã bị thay từ lâu → **thu hồi cả family**, cả RT2 của người thật. Hai bên đều phải đăng nhập lại, và kẻ gian mất quyền.
- **Thời gian ân hạn 30 giây (REFRESH_RACE)** — hai tab cùng gia hạn với RT1 trong vài mili giây. Không có ân hạn thì tab chậm hơn bị coi là kẻ trộm, người dùng bị đá ra. Trong 30 giây trả 409, không phát token mới, không thu hồi.
- **Băm khác nhau cho mật khẩu và token** — mật khẩu do người đặt, dễ đoán → BCrypt (chậm cố ý). Refresh token ngẫu nhiên 256 bit, không đoán được → SHA-256 đủ. DB bị lộ thì cả hai đều không dùng lại được.
- **Open redirect** — `/login?redirect=https://kho-gia.vn`: đăng nhập thật xong bị đưa sang trang giả. `safeRedirect` chỉ nhận đường dẫn bắt đầu bằng `/` (không phải `//`).
- **Ẩn nút ở frontend ≠ phân quyền** — `permissions.ts` chỉ để giao diện gọn. Gõ thẳng URL hoặc gọi API bằng curl vẫn bị backend chặn: `AuthorizationMatrixTest` có 99 ca (33 endpoint × 3 vai trò) + 33 ca chưa đăng nhập.

### Lỗi đã gặp

- **DSL `oauth2ResourceServer()` tắt CSRF ngầm** — DSL này giả định token nằm ở header `Authorization` (trình duyệt không tự gửi), nên tự bỏ kiểm CSRF cho mọi request có token. Ta để token trong cookie, nên điều đó thành lỗ hổng: `POST /api/customers` kèm cookie mà không có CSRF vẫn chạy tới controller (test nhận 400 thay vì 403). Sửa: tự lắp `BearerTokenAuthenticationFilter` bằng `http.addFilter(...)`, vẫn dùng `JwtDecoder` của thư viện. **Bài học:** mặc định của framework đúng *với giả định của nó*; đổi giả định (header → cookie) thì phải kiểm lại các mặc định.
- **UPDATE hàng loạt không cập nhật bộ nhớ của Hibernate** — `revokeFamily` (`@Modifying` JPQL) ghi thẳng DB. Entity `RefreshToken` đang nằm trong persistence context vẫn có `revokedAt = null`, nên lần đọc sau trong cùng transaction thấy bản cũ. Sửa: `clearAutomatically = true` (kèm `flushAutomatically` để ghi thay đổi đang chờ trước).
- **Thu hồi phải được commit dù trả 401** — phát hiện dùng lại token mà `throw` exception trong `@Transactional` thì rollback mất việc thu hồi. Vì vậy `refresh()` trả về kiểu `RefreshResult` (sealed interface: `Renewed` / `Race` / `Rejected`) và controller đổi thành HTTP status.
- **Postprocessor `csrf()` của spring-security-test sửa filter dùng chung** — nó thay `CsrfTokenRepository` của `CsrfFilter` (một bean cho cả Spring context) bằng bản lưu trong session. Từ test thứ hai trở đi, cookie `XSRF-TOKEN` thật không còn được tạo, nên test chạy riêng thì qua, chạy cả class thì lỗi. Luồng CSRF thật được tách sang `CsrfDoubleSubmitTest` với context riêng.
- **Thứ tự kiểm "bị khóa" của `DaoAuthenticationProvider`** — mặc định kiểm *trước* mật khẩu, nên đoán bừa mật khẩu vẫn biết được "tài khoản tồn tại và bị khóa". Đổi: `preAuthenticationChecks` rỗng, `postAuthenticationChecks = AccountStatusUserDetailsChecker`.
- **Access token vừa hết hạn làm hỏng chính lời gọi gia hạn** — cookie `GW_AT` có `Path=/api` nên vẫn được gửi tới `/api/auth/refresh`. Filter thấy token hết hạn → 401 ngay, đúng lúc cần gia hạn. Resolver bỏ qua các endpoint công khai.
- **`CHAR(64)` vs `String`** — Hibernate mặc định coi `String` là `VARCHAR` → `ddl-auto: validate` báo sai kiểu. Không sửa migration đã chạy (lệch checksum Flyway); thêm `@JdbcTypeCode(SqlTypes.CHAR)` ở entity.
- **VS Code ghi `.class` lỗi vào `target/`** (lại gặp, xem module Nhà cung cấp) → luôn `mvn clean` trước khi chạy test.
- **Đã thử bỏ khóa dòng** (`@Lock` trên `findForUpdateByTokenHash`): `RefreshConcurrencyTest` cho 5 luồng cùng gia hạn và cả 5 đều nhận token mới, tức một token cũ đẻ ra 5 phiên. Có khóa: đúng 1 `Renewed` + 4 `Race`.

### Câu hỏi ôn tập (để cuối dự án)

1. Vì sao lưu token trong `localStorage` thì không cần chống CSRF, còn lưu trong cookie thì bắt buộc? Đổi lại, `localStorage` mất gì?
2. Quản lý hệ thống khóa tài khoản lúc 9:00. Đến lúc nào người đó mới thật sự mất quyền? Muốn tức thì thì phải đánh đổi gì?
3. Kẻ gian chép được refresh token của chị Lan lúc 9:00. Mô tả chuyện xảy ra nếu chị Lan gia hạn lúc 9:05 và kẻ gian dùng token lúc 10:00.
4. Vì sao `AuthorizationMatrixTest` kiểm cả "sai vai trò → 403" chứ không chỉ "đúng vai trò → 200"?
