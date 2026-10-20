# ADR-0003: Xác thực và phân quyền

## Trạng thái

✅ **Đã chấp nhận — 2026-10-20**, đã triển khai (migration V14, package `auth`, frontend `features/auth`, `features/users`).

Trả lời các câu hỏi mở: đồng ý `spring-boot-starter-oauth2-resource-server`; ma trận mục 5 giữ nguyên; **một người kiêm được nhiều vai trò**; thời hạn 15 phút / 8 giờ / 24 giờ; chấp nhận khóa tài khoản trễ tối đa 15 phút.

Thay đổi so với bản đề xuất (phát hiện khi triển khai):
- Vai trò tách thành bảng `app_user_role`, không còn cột `role` (vì kiêm nhiều vai trò).
- **Không dùng DSL `oauth2ResourceServer()`**. DSL này tự bỏ kiểm CSRF cho mọi request có bearer token, vì giả định token nằm ở header. Token của dự án nằm trong cookie, trình duyệt tự gửi đi, nên CSRF phải được giữ. Thay vào đó tự lắp `BearerTokenAuthenticationFilter` + `JwtAuthenticationProvider` + `JwtDecoder`: cùng các lớp của thư viện, chỉ không đi qua DSL. `AuthApiTest.shouldRequireCsrfOnWrites` phát hiện lỗ hổng này (trả 400 thay vì 403).

## Bối cảnh

- Hệ thống chưa có đăng nhập. Đưa lên Internet thì ai biết địa chỉ cũng sửa được tồn, đảo phiếu, xem công nợ.
- Yêu cầu đã chốt (2026-10-20):
  - Spring Security theo chuẩn Spring Boot 3+ (dự án dùng Boot 3.3.5 → Spring Security 6.3).
  - Access token và refresh token tách biệt, có thời hạn, gia hạn, đăng xuất, thu hồi.
  - **Không** lưu token trong `localStorage` / `sessionStorage`.
  - Token gửi tự động qua cookie → phải chống CSRF.
  - Phân quyền ở backend theo role, không chỉ ẩn nút trên frontend.
  - Dùng OAuth2 Resource Server + `JwtDecoder`, không tự viết bộ xác minh JWT.
  - 3 vai trò nghiệp vụ (overview.md); hết phiên khi không thao tác 8 giờ.
- **Mâu thuẫn tài liệu:** `system-design.md` ghi "Spring Security + JWT" ở mục mở rộng nhưng không nói token lưu ở đâu. ADR này thay cho dòng đó sau khi được duyệt.

## Đánh giá phương án lưu token

| Phương án | Cách làm | Ưu | Nhược | Kết luận |
|---|---|---|---|---|
| A. Bearer header + `localStorage` | JS giữ token, gắn `Authorization` | Không cần chống CSRF | Lỗi XSS nào cũng đọc trộm được token | ❌ Bị loại theo yêu cầu |
| **B. JWT trong cookie HttpOnly + Secure** | Backend tự phát JWT, trình duyệt giữ trong cookie JS không đọc được | Một ứng dụng, không thêm dịch vụ; JS không chạm token; dùng `JwtEncoder`/`JwtDecoder` có sẵn | Cookie tự gửi theo request → **phải chống CSRF**; access token đã phát không thu hồi tức thì được | ✅ **Đề xuất** |
| C. BFF (Backend-for-Frontend) | Một Authorization Server riêng (Keycloak / Spring Authorization Server) phát token; một tầng BFF (`oauth2Login`) giữ token phía server, trình duyệt chỉ có session cookie | Đúng khuyến nghị OAuth cho ứng dụng trình duyệt khi có IdP; token không bao giờ tới trình duyệt | Thêm 1–2 dịch vụ phải vận hành; luồng OAuth đầy đủ (redirect, code, PKCE) | ⏸ Chỉ cần khi có SSO, nhiều ứng dụng dùng chung tài khoản, hoặc đăng nhập Google |

**Vì sao B mà không phải C:** ở dự án này React và API chạy **cùng một tên miền** (Nginx khi deploy, Vite proxy khi dev). Bản thân backend Spring đã đứng ở vị trí của BFF: trình duyệt chỉ giữ cookie mà JS không đọc được. C chỉ thêm giá trị khi có nhà cung cấp danh tính bên ngoài.

## Quyết định đề xuất

### 1. Hai loại token

| | Access token | Refresh token |
|---|---|---|
| Dạng | JWT ký HS256 (`NimbusJwtEncoder`) | Chuỗi ngẫu nhiên 256 bit (không phải JWT) |
| Thời hạn | **15 phút** | Hết hạn sau **8 giờ không dùng** (mỗi lần gia hạn tính lại 8 giờ) + tối đa **24 giờ** kể từ lúc đăng nhập |
| Xác minh | `JwtDecoder` của Resource Server: chữ ký, `exp`, `iss`. **Không truy vấn DB** | Tra DB theo SHA-256 của token |
| Thu hồi | Không thu hồi tức thì được → sống tối đa 15 phút | Thu hồi ngay (đánh dấu `revoked_at`) |
| Cookie | `GW_AT` · HttpOnly · Secure · SameSite=Strict · `Path=/api` | `GW_RT` · HttpOnly · Secure · SameSite=Strict · **`Path=/api/auth`** (chỉ gửi tới endpoint gia hạn/đăng xuất) |

- Claim của access token: `sub` = user id, `username`, `roles`, `iss`, `iat`, `exp`, `jti`.
- **HS256 (khóa đối xứng):** chỉ một ứng dụng vừa ký vừa xác minh. Khóa ≥ 32 byte đọc từ `.env`. Cần RS256 (cặp khóa) khi có dịch vụ khác chỉ xác minh mà không được phép ký.
- **Vì sao refresh token không phải JWT và được lưu hash:** cần thu hồi được, nên kiểu gì cũng phải tra DB. Lưu SHA-256 thay vì token gốc: lộ bảng thì không dùng được token. Không cần BCrypt vì token ngẫu nhiên 256 bit, không đoán được như mật khẩu.

### 2. Gia hạn: rotation + phát hiện dùng lại

```
Đăng nhập ──▶ RT1 (family F)
Gia hạn với RT1 ──▶ RT1 bị đánh dấu "đã thay bằng RT2", phát RT2 + access token mới
Gia hạn với RT1 lần nữa (kẻ gian đánh cắp RT1)
   ├─ RT1 bị thay < 30 giây trước → coi là 2 tab gia hạn cùng lúc: trả 401 REFRESH_RACE, FE thử lại (cookie đã là RT2)
   └─ Quá 30 giây → coi là token bị trộm: THU HỒI CẢ FAMILY F → mọi thiết bị của phiên đó phải đăng nhập lại
```

- Dòng refresh token được khóa `SELECT ... FOR UPDATE` khi gia hạn, giống cách khóa phiếu khi hoàn tất. Hai request cùng gia hạn thì request sau phải chờ request trước.
- Frontend chỉ chạy **một** lần gia hạn tại một thời điểm (single-flight). Nhiều API cùng nhận 401 thì cùng chờ chung một promise gia hạn.

### 3. Đăng xuất, thu hồi, đăng nhập đồng thời

| Tình huống | Xử lý |
|---|---|
| Đăng xuất | Thu hồi family hiện tại, xóa 2 cookie. Access token cũ (nếu bị chép) còn sống ≤ 15 phút |
| Đăng nhập nhiều thiết bị | Được phép; mỗi lần đăng nhập là một family riêng; đăng xuất máy này không ảnh hưởng máy kia |
| Đổi mật khẩu | Thu hồi mọi family **trừ** phiên hiện tại |
| ADMIN khóa tài khoản / đổi vai trò / đặt lại mật khẩu | Thu hồi **mọi** family của người đó → tối đa 15 phút sau là mất quyền |
| Hết 8 giờ không thao tác | Gia hạn thất bại → FE chuyển về trang đăng nhập, giữ lại đường dẫn đang xem |

> **Giới hạn chấp nhận:** khóa tài khoản không có hiệu lực tức thì mà trễ tối đa 15 phút. Muốn tức thì thì mỗi request phải tra DB (claim `token_version`), tức là mất ưu điểm "không truy vấn DB" của JWT. Chỉ làm nếu bạn yêu cầu.

### 4. Chống CSRF

Cookie tự gửi theo mọi request tới cùng tên miền. Nếu không chặn, một trang độc hại có thể đặt form ngầm `POST /api/sales-orders/5/reverse` và trình duyệt sẽ tự gửi kèm cookie đăng nhập. Chặn bằng 2 lớp:

1. **SameSite=Strict** trên cookie token: trình duyệt không gửi cookie khi request xuất phát từ trang khác.
2. **CSRF token kiểu double-submit** (`CookieCsrfTokenRepository` của Spring Security):
   - Cookie `XSRF-TOKEN` *không* HttpOnly. JS đọc giá trị rồi gửi lại trong header `X-XSRF-TOKEN` với mọi `POST/PUT/DELETE`.
   - Trang độc hại không đọc được cookie của tên miền khác nên không tạo được header này → 403.
   - Áp dụng cả cho `/api/auth/login`, để chặn kiểu "login CSRF": ép nạn nhân đăng nhập vào tài khoản của kẻ gian.
   - Theo mẫu SPA trong tài liệu Spring Security 6.3: `CsrfTokenRequestAttributeHandler` + filter nạp token để cookie luôn có sẵn.

### 5. Phân quyền

- Vai trò — một tài khoản có **một hoặc nhiều** vai trò, quyền = hợp quyền các vai trò:

| Mã | Vai trò |
|---|---|
| `WAREHOUSE_MANAGER` | Quản lý kho |
| `SALES_STAFF` | Nhân viên bán hàng |
| `SYSTEM_ADMIN` | Quản lý hệ thống |

- Quy tắc truy cập đặt **một chỗ** trong `SecurityFilterChain` (theo method + URL), kèm test cho từng ô của ma trận dưới đây. Frontend ẩn menu/nút chỉ để tiện dùng, không phải lớp bảo vệ.
- Ma trận đề xuất (R = xem, W = thêm/sửa/thao tác, — = 403):

| Chức năng | Quản lý kho | Bán hàng | Quản lý hệ thống |
|---|---|---|---|
| Danh mục, Sản phẩm, Kho — xem | R | R | R |
| Danh mục, Sản phẩm, Kho — thêm/sửa/ngừng | — | — | W |
| Nhà cung cấp, liên kết NCC–SP | W | — | W |
| Phiếu nhập (lập, hoàn tất, hủy, đảo) | W | — | W |
| Khách hàng, bảng giá riêng | R | W | W |
| Phiếu bán, thu tiền, hoàn khoản thu | R | W | W |
| Công nợ | — | R | R |
| Tồn kho, lịch sử kho | R | R | R |
| Nhân viên | — | — | W |
| Chấm công | W | — | W |
| Bảng lương (xem, chốt, hủy chốt) | — | — | W |
| Người dùng (tạo, khóa, đổi vai trò, đặt lại mật khẩu) | — | — | W |

- 401/403 trả đúng envelope trong contract-spec (`UNAUTHORIZED` / `FORBIDDEN`), không trả trang lỗi mặc định của Spring.

### 6. Schema đề xuất (migration V14)

```sql
CREATE TABLE app_user (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username       VARCHAR(50)  NOT NULL,          -- UNIQUE INDEX (lower(username))
    password_hash  VARCHAR(100) NOT NULL,          -- "{bcrypt}$2a$10$..." (DelegatingPasswordEncoder)
    full_name      VARCHAR(100) NOT NULL,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at  TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE app_user_role (                    -- một người kiêm nhiều vai trò
    user_id  BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    role     VARCHAR(30) NOT NULL,                 -- CHECK (role IN ('WAREHOUSE_MANAGER','SALES_STAFF','SYSTEM_ADMIN'))
    PRIMARY KEY (user_id, role)
);

CREATE TABLE refresh_token (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id            BIGINT      NOT NULL REFERENCES app_user (id),
    family_id          UUID        NOT NULL,       -- một lần đăng nhập = một family
    token_hash         CHAR(64)    NOT NULL,       -- SHA-256 hex; UNIQUE
    expires_at         TIMESTAMPTZ NOT NULL,       -- hạn 8 giờ không thao tác
    family_expires_at  TIMESTAMPTZ NOT NULL,       -- hạn tuyệt đối 24 giờ từ lúc đăng nhập
    replaced_by_id     BIGINT      REFERENCES refresh_token (id),
    replaced_at        TIMESTAMPTZ,
    revoked_at         TIMESTAMPTZ,
    revoke_reason      VARCHAR(30),                -- LOGOUT | REUSE_DETECTED | PASSWORD_CHANGED | ADMIN_ACTION
    user_agent         VARCHAR(200),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_refresh_token_expiry CHECK (expires_at <= family_expires_at)
);
-- index: (user_id), (family_id)
```

- Tài khoản `SYSTEM_ADMIN` đầu tiên được tạo **khi khởi động**, chỉ khi bảng `app_user` đang trống, từ biến môi trường `BOOTSTRAP_ADMIN_USERNAME` / `BOOTSTRAP_ADMIN_PASSWORD`. Mật khẩu không nằm trong code, migration hay seed.
- `refresh_token` **không** phải bảng bất biến như `stock_transaction`: được phép cập nhật `revoked_at`, `replaced_by_id`. Bản ghi hết hạn sẽ được dọn định kỳ (làm sau).
- **Ngoài phạm vi ADR này:** ghi "ai lập / hoàn tất phiếu" vào `stock_document` (đổi schema đã duyệt — bước riêng), giới hạn số lần đăng nhập sai (làm bằng `limit_req` của Nginx ở bước deploy).

### 7. API

| Endpoint | Ai gọi | Kết quả |
|---|---|---|
| `GET /api/auth/csrf` | Công khai | 204, đặt cookie `XSRF-TOKEN` |
| `POST /api/auth/login` `{ username, password }` | Công khai (cần CSRF) | 200 + 2 cookie + `{ id, username, fullName, role }`. Sai tên/mật khẩu → 401, thông báo chung "Sai tên đăng nhập hoặc mật khẩu" (không lộ tên đăng nhập nào tồn tại) |
| `POST /api/auth/refresh` | Có `GW_RT` | 200 + cookie mới; không hợp lệ/hết hạn → 401 và xóa cookie |
| `POST /api/auth/logout` | Có `GW_RT` | 204, thu hồi family, xóa cookie |
| `GET /api/auth/me` | Đã đăng nhập | Thông tin người dùng + vai trò (FE dựa vào đây để hiện menu) |
| `PUT /api/auth/me/password` | Đã đăng nhập | Đổi mật khẩu (cần mật khẩu cũ) |
| `GET/POST /api/users`, `PUT /api/users/{id}`, `POST /api/users/{id}/reset-password` | `SYSTEM_ADMIN` | Quản lý tài khoản |
| `/api/health`, `/actuator/health` | Công khai | Như cũ (health check khi deploy) |

### 8. Frontend

- Trang `/login`. Mọi route khác cần đăng nhập. Menu hiện theo vai trò; route không đủ quyền hiện trang 403.
- `api/client.ts`:
  - Đọc cookie `XSRF-TOKEN`, gắn header `X-XSRF-TOKEN` cho mọi request ghi.
  - Gặp 401 → gọi `/api/auth/refresh` một lần (single-flight) rồi gửi lại request gốc. Gia hạn thất bại → chuyển về `/login?redirect=...`.
  - Không đọc, không lưu token: token nằm trong cookie HttpOnly, JS không chạm được. `fetch` cùng tên miền tự gửi cookie.

### 9. CORS, HTTPS, môi trường

- **CORS:** không bật. Trình duyệt chỉ gọi cùng tên miền (Nginx khi deploy, Vite proxy khi dev). Không thêm `@CrossOrigin`; Spring Security từ chối request khác nguồn theo mặc định.
- **HTTPS:**
  - Cookie `Secure` chỉ được trình duyệt gửi qua HTTPS. Riêng `http://localhost` là ngoại lệ, nên dev vẫn chạy được.
  - ⚠️ **Hệ quả cho bước deploy:** chạy HTTP qua IP thì đăng nhập **không hoạt động**, còn tắt `Secure` thì mật khẩu và token đi dạng chữ thường trên mạng. Vì vậy phải có HTTPS ngay từ lần deploy đầu, tức là cần tên miền. Có thể dùng tên miền miễn phí, ví dụ DuckDNS, rồi cấp chứng chỉ Let's Encrypt.
- **Secrets:** `JWT_SECRET` (≥ 32 byte ngẫu nhiên), `BOOTSTRAP_ADMIN_*` để trong `.env`, có tên biến trong `.env.example`.

### 10. Dependency

| Artifact | Vai trò | Trạng thái |
|---|---|---|
| `spring-boot-starter-security` | Chuỗi filter bảo mật, `AuthenticationManager`, `PasswordEncoder`, CSRF | ✅ Đã đồng ý |
| `spring-boot-starter-oauth2-resource-server` | `JwtDecoder` / `JwtEncoder` (Nimbus), filter đọc và xác minh access token | ❓ **Chờ đồng ý** |
| `spring-security-test` (test) | Giả lập người dùng/vai trò trong MockMvc, CSRF trong test | ✅ Đã đồng ý |

Cùng phiên bản do Spring Boot 3.3.5 quản lý; không thêm thư viện JWT nào khác.

## Spring Security hoạt động thế nào (áp vào dự án)

Mỗi request đi qua một **chuỗi filter** (`SecurityFilterChain`) trước khi tới controller:

```
Request ──▶ CsrfFilter ──▶ BearerTokenAuthenticationFilter ──▶ AuthorizationFilter ──▶ Controller
              (tự lắp, xem "Thay đổi so với bản đề xuất")
              │                     │                                  │
              │ POST/PUT/DELETE     │ CookieBearerTokenResolver         │ so vai trò với quy tắc URL
              │ thiếu/sai           │ lấy JWT từ cookie GW_AT →         │ không đủ quyền
              │ X-XSRF-TOKEN → 403  │ JwtDecoder kiểm chữ ký + exp →    │ → AccessDeniedHandler → 403 envelope
              │                     │ JwtAuthenticationConverter:       │
              │                     │ claim roles → ROLE_SALES_STAFF    │
              │                     │ → lưu vào SecurityContext         │
              │                     │ hỏng/hết hạn → EntryPoint → 401   │
```

- **Đăng nhập** không đi qua `formLogin` mặc định mà qua controller riêng `POST /api/auth/login`:
  - `AuthenticationManager` → `DaoAuthenticationProvider` → `UserDetailsService` đọc `app_user` → `PasswordEncoder` so BCrypt.
  - Thành công thì `JwtEncoder` ký access token, service tạo refresh token, rồi đặt 2 cookie.
- **Stateless:** `SessionCreationPolicy.STATELESS`. Server không giữ `HttpSession`; mỗi request tự mang đủ thông tin trong JWT.
- Ví dụ: nhân viên bán hàng bấm "Hoàn tất" phiếu PX000007:
  1. CSRF hợp lệ.
  2. JWT hợp lệ, vai trò `SALES_STAFF`.
  3. Quy tắc `POST /api/sales-orders/**` cho `SALES_STAFF` → đi tiếp vào `SalesOrderService`.
  4. Cùng request đó của quản lý kho → 403, service không bao giờ chạy.

## Kế hoạch kiểm thử (khi triển khai)

- Từng ô của ma trận phân quyền: đúng vai trò → 2xx; sai vai trò → 403; chưa đăng nhập → 401.
- POST không có / sai CSRF token → 403.
- Access token hết hạn, sai chữ ký, sai `iss` → 401.
- Gia hạn: rotation; dùng lại token cũ sau 30 giây → thu hồi cả family; hai gia hạn đồng thời (test đồng thời như `SalesOrderConcurrencyTest`).
- Hết 8 giờ không thao tác; hết hạn tuyệt đối 24 giờ (dùng `Clock` giả, không chờ thật).
- Đăng xuất chỉ ảnh hưởng thiết bị đó; khóa tài khoản thu hồi mọi phiên.
- Cookie có HttpOnly, Secure, SameSite=Strict và đúng Path.
- Sai mật khẩu và tên đăng nhập không tồn tại trả cùng một thông báo.
- Test hiện có (295) chạy với người dùng giả lập (`@WithMockUser` / `jwt()` của spring-security-test).

## Còn mở / làm sau

- Dọn định kỳ các dòng `refresh_token` đã hết hạn.
- Giới hạn số lần đăng nhập sai: `limit_req` của Nginx ở bước deploy.
- Hai quản trị viên cùng lúc hạ quyền lẫn nhau có thể làm hệ thống không còn ai là quản trị viên (BR-AUTH-05 chưa khóa dòng). Rủi ro thấp với quy mô hiện tại.
- Ghi "ai lập / hoàn tất phiếu" vào chứng từ: thay đổi schema riêng, cần duyệt.
