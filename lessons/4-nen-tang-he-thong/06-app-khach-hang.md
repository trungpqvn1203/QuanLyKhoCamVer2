# App khách hàng tự đặt hàng — hai vùng đăng nhập

[← Mục lục kho bài học](../README.md)

## Module: App khách (2026-11-03, V27, ADR-0005)

Bài toán: mỗi ngày một người vừa quản kho vừa nghe hàng trăm cuộc gọi đặt hàng. Khách tự đặt trên điện thoại → đơn vào thẳng danh sách **Chờ xác nhận** kênh `APP` (dùng lại V25), cửa hàng chỉ còn gọi lại báo giá và xác nhận.

### Một backend, nhiều client — monorepo hay polyrepo

- **Backend không biết client là web hay app.** Nó chỉ cung cấp API. Web quản trị, app khách (`/khach`, PWA trong cùng project React) và sau này app React Native đều gọi cùng một Spring Boot, cùng một DB. Ví dụ: đơn PX000231 khách đặt từ app hiện ngay ở màn hình "Phiếu bán" của chủ cửa hàng, vì cả hai đọc cùng bảng `stock_document`.
- **Monorepo** (một repo: `backend/`, `frontend/`, sau này `mobile/`, `packages/shared/`) hợp team nhỏ, một người làm cả hệ thống, sửa API và client trong cùng một commit. **Polyrepo** (mỗi client một repo) hợp công ty lớn, mỗi team phát hành độc lập (Shopee, Grab…). Dự án này chọn monorepo.
- **Thêm app native thì phải làm thêm gì:** giao diện (Ant Design chỉ chạy trên web) và router không dùng lại được. Quan trọng nhất là **cách giữ token**: web giữ trong cookie HttpOnly, còn app native giữ trong Keychain / Keystore và gửi header `Authorization`. Lúc đó cần chế độ "bearer header" cho chuỗi portal (không cần CSRF vì trình duyệt không tự gửi header). Ghi sẵn ở ADR-0005 mục 8.

### Hai vùng đăng nhập — vì sao không thêm vai trò `CUSTOMER` vào `app_user`

- **Lỗ hổng tìm thấy khi đọc code:** nhiều quy tắc của nhân viên chỉ là `authenticated()`, ví dụ `GET /api/customers/**`, `/api/sales-orders/**`, `/api/reports/**`. `JwtDecoder` chỉ kiểm chữ ký + `iss` + hạn. Nếu token khách ký cùng khóa, cùng `iss`, thì khách chỉ cần tự đặt token vào cookie `GW_AT` bằng curl là **đọc được toàn bộ khách khác, phiếu bán, báo cáo**. Thêm vai trò `CUSTOMER` rồi sửa từng `authenticated()` cũng được, nhưng quên một chỗ (kể cả ở module viết sau này) là lộ dữ liệu ngay.
- **Tách ở tầng giải mã, không phải tầng quy tắc:** token khách ký bằng **khóa khác** (`HMAC-SHA256(JWT_SECRET, "portal-v1")`, dẫn xuất từ khóa nhân viên, không thêm biến môi trường) **và** dùng **`iss` khác** (`grain-warehouse-portal`). Decoder nhân viên không xác minh nổi chữ ký token khách. Kể cả khi sau này ai đó lỡ dùng chung khóa, validator `iss` vẫn chặn. Test `customerTokenShouldNotWorkOnStaffApi` đặt token khách vào `GW_AT` rồi gọi 5 API nhân viên: tất cả trả 401.
- **Hai `SecurityFilterChain`:** chuỗi portal có `securityMatcher("/api/portal/**")` và `@Order(1)`. Chuỗi nhân viên khớp mọi URL nên phải đứng sau. Request tới `/api/portal/**` không bao giờ đi qua quy tắc của nhân viên. Cookie `GW_AT` (Path=`/api`) vẫn được trình duyệt gửi tới `/api/portal/...`, nhưng chuỗi portal chỉ đọc `GW_CAT`.
- **Bẫy "hai bean cùng kiểu":** nếu khai báo decoder của khách là `@Bean JwtDecoder`, Spring sẽ không biết tiêm bean nào vào `SecurityConfig` và `TokenService` của nhân viên (lỗi `NoUniqueBeanDefinitionException`), hoặc tệ hơn, tiêm nhầm. Vì vậy encoder/decoder của khách nằm **bên trong** một `@Component PortalTokenCodec`, không phải bean riêng.

### Dữ liệu chỉ lấy từ token — IDOR và 404 thay vì 403

- **IDOR** (Insecure Direct Object Reference): đổi `/orders/231` thành `/orders/232` để đọc đơn người khác. Cách chặn: không API portal nào nhận `customerId`. Controller chỉ đọc `sub` của token, service lọc theo khách đó.
- Đơn của khách khác trả **404 với đúng thông báo của đơn không tồn tại**, không trả 403. 403 nghĩa là "đơn này có thật nhưng không phải của bạn", tức là đã lộ thông tin. Ở `SalesOrderService.lockOwnedPending`: kiểm **chủ đơn trước, trạng thái sau**. Đảo thứ tự thì kẻ dò sẽ nhận "đã được cửa hàng tiếp nhận" cho đơn của người khác.
- **Chỉ chép trường được phép ra ngoài:** `PortalOrderResponse.from(SalesOrderResponse)` liệt kê từng trường, không trả thẳng DTO của nhân viên. Thêm trường nội bộ mới vào phiếu bán thì nó **không tự động** lộ ra app. Test kiểm `warehouseName`, `createdBy`, `paidAmount` không có trong JSON.

### Khóa tạm khi sai mật khẩu — ba chi tiết dễ sai

1. **Phải commit dù request trả lỗi:** sai mật khẩu thì ném `BadCredentialsException`. Trong `@Transactional` mặc định, exception làm rollback cả lần tăng `failed_login_count`, nên sẽ không bao giờ khóa được. Cách sửa: `@Transactional(noRollbackFor = AuthenticationException.class)`. Phiên nhân viên giải bài tương tự bằng cách trả kết quả thay vì ném exception (`RefreshResult`).
2. **Khóa dòng trước khi đếm:** 5 request sai gửi cùng lúc mà không có `FOR UPDATE` thì cùng đọc "đã sai 0 lần" rồi cùng ghi 1 (lost update, giống bài thu tiền BR-SAL-11). **Đã thử bỏ `@Lock`:** `PortalConcurrencyTest` fail ("Expecting value to be true but was false"), tài khoản không bị khóa. Có khóa thì pass.
3. **Đo thời gian cũng lộ thông tin:** BCrypt chậm cố ý (~100 ms). Nếu SĐT không có tài khoản mà trả lỗi ngay, kẻ dò đo thời gian phản hồi là biết SĐT nào đã có tài khoản. Cách chặn: vẫn so mật khẩu với một hash giả (`dummyHash`).

### Frontend: hai phiên trong một app React

- `api/client.ts` chia theo **vùng**: 401 ở `/api/portal/**` thì gia hạn qua `/api/portal/auth/refresh` và báo hết phiên cho `PortalAuthProvider`, không đẩy khách về trang đăng nhập của nhân viên. Test `client.test.ts` kiểm cả hai chiều.
- Route `/khach/*` nằm **ngoài** nhánh `/`, vì nhánh đó bọc `RequireAuth` (phiên nhân viên) và `AppLayout` (menu quản trị). Route được tải lười (`lazy()`) nên nhân viên không tải code của app khách.
- Manifest PWA chỉ gắn khi đang ở `/khach`, để trang quản trị không bị trình duyệt đề nghị "cài app".

### Lỗi đã gặp

- **Mã hóa ký tự trong test:** `MockHttpServletResponse.getContentAsString()` không đọc charset của header nên giải mã theo ISO-8859-1. "Cám" thành "CÃ¡m" và assertion fail, dù API trả đúng UTF-8. Cách sửa: `getContentAsString(StandardCharsets.UTF_8)`. So sánh hai chuỗi lỗi với nhau vẫn "đúng" vì cả hai cùng bị giải mã sai, nên lỗi này dễ bị che.
- **`npx tsc --noEmit` không kiểm tra gì:** gặp lại từ module trước. Lệnh đúng là `npx tsc --noEmit -p tsconfig.app.json`.
- **Lint `only-export-components`:** khai báo `lazy(...)` trong `router.tsx`, file đang export `router`, làm hỏng Fast Refresh. Cách sửa: tách phần tải lười ra component riêng (`CustomerPortalEntry`).

## Rà soát bảo mật trước khi mở cho khách thật (2026-11-04, V29)

Tách vùng đăng nhập đã chắc, nhưng rà lại theo câu hỏi "người ngoài làm được gì với **dữ liệu của cửa hàng**" thì ra ba lỗ mà không test nào trước đó bắt được.

### Một cột, hai người đọc = rò rỉ

- Đơn app chỉ có cột `note`. Khách ghi "Giao buổi sáng"; quản kho xác nhận, ghi đè thành "Khách nợ nhiều, thu tiền trước". App khách đọc `note`, nên khách đọc được câu đó.
- Lỗi không nằm ở phần đăng nhập mà ở **mô hình dữ liệu**: một trường do hai bên ghi, nhưng bên ngoài được đọc. Chép từng trường ra DTO (bài trên) không cứu được, vì chính trường `note` đã được phép ra ngoài.
- Cách sửa: **mỗi bên một cột**. `customer_note` chỉ app ghi, `note` chỉ cửa hàng ghi, app chỉ đọc `customer_note`. CHECK `customer_note IS NULL OR order_channel = 'APP'`.
- Lý do hủy cũng thế: cửa hàng ghi "Khách bom hàng nhiều lần" thì khách không được đọc. App chỉ hiện nguyên văn khi lý do bắt đầu bằng "Khách hủy qua app"; còn lại hiện câu chung.
- **Chuyển dữ liệu cũ theo hướng an toàn:** chỉ chép những đơn chắc chắn là lời khách (còn `PENDING`, chưa ai sửa). Đơn đã xác nhận thì bỏ: thà mất một câu ghi chú cũ còn hơn lộ ghi chú nội bộ.

### Chặn làm ngập: giới hạn + khóa trước khi đếm

- Không có giới hạn thì một tài khoản bị lộ mật khẩu có thể gửi 500 đơn, mỗi đơn 200 dòng, mỗi dòng SL 2 tỷ, và danh sách Chờ xác nhận của quản kho thành vô dụng.
- "Tối đa 5 đơn chờ" là kiểu **kiểm rồi ghi** (check-then-act), cùng loại với thu tiền vượt nợ: hai request cùng lúc cùng đếm 4, rồi cùng tạo. Cách chặn: `findForUpdateWithCustomerById` khóa dòng `customer_account` trước khi đếm, nên request thứ hai phải chờ rồi đếm ra 5. Test `concurrentOrdersShouldRespectPendingLimit`: 8 request cùng lúc thì đúng 5 thành công.
- Giới hạn chỉ cho app: SL tối đa nằm ở DTO riêng `PortalOrderLineRequest`, không sửa `SaleRequestLineRequest` mà nhân viên đang dùng.

### Jackson lặng lẽ làm tròn số

- Khách gửi `"quantity": 1.5` vào trường `Integer`: Jackson mặc định **cắt thành 1** (`ACCEPT_FLOAT_AS_INT`), không báo lỗi. Đặt 1,5 bao thì nhận đơn 1 bao.
- Cách sửa ở app: đọc vào `BigDecimal` rồi kiểm `@Digits(fraction = 0)`. Hệ quả nhỏ: `2.0` cũng bị từ chối, vì Hibernate Validator đếm cả số 0 sau dấu phẩy. App luôn gửi số nguyên nên chấp nhận được.

### Giới hạn theo IP ở Nginx — vì sao chỉ cho đăng nhập

- Khóa 5 lần sai chỉ chặn dò **một** tài khoản. Kẻ xấu đổi chiến thuật: thử `12345678` cho 1.000 SĐT, mỗi SĐT một lần, nên không tài khoản nào bị khóa (password spraying). Chặn bằng `limit_req` theo IP.
- Không giới hạn `refresh`: refresh token là chuỗi ngẫu nhiên 256 bit nên không dò được, và mỗi khách gọi 15 phút một lần. Nhà mạng di động dùng **CGNAT** (nhiều thuê bao chung một IP), giới hạn refresh có thể chặn nhầm cả nhóm khách thật.
- Chạy `npm run dev` không qua Nginx thì không có giới hạn này; chỉ có khi chạy bằng Docker.

### Lỗi đã gặp

- **Hai nhánh cùng dùng số migration:** nhánh chuyến giao có V28 và đã chạy vào DB test. Nhánh này tách từ main, thêm V29 → Flyway báo "applied migration not resolved locally: 28". Khi chạy test, em thêm `-Dspring.flyway.ignore-migration-patterns=*:missing` **chỉ trên dòng lệnh**, không ghi vào cấu hình. Bài học: số phiên bản migration là tài nguyên dùng chung giữa các nhánh, nên gộp nhánh nào trước thì nhánh sau phải đánh lại số migration của mình.

### Câu hỏi ôn tập (để cuối dự án)

1. Nếu chỉ tách `iss` mà dùng chung khóa ký, kịch bản nào vẫn lộ dữ liệu? Ngược lại, nếu chỉ tách khóa mà dùng chung `iss` thì sao?
2. Vì sao đơn của khách khác trả 404 chứ không phải 403? Trong `lockOwnedPending`, đổi thứ tự "kiểm chủ đơn" và "kiểm trạng thái" thì lộ ra điều gì?
3. Tại sao `noRollbackFor` là bắt buộc ở đăng nhập app khách, mà đăng nhập nhân viên lại không cần?
4. Khi thêm app React Native, phần nào của backend phải sửa, và vì sao endpoint mới đó không cần CSRF?
5. Monorepo và polyrepo khác nhau thế nào? Khi nào dự án này nên tách `mobile/` thành repo riêng?
6. Vì sao chép từng trường ra `PortalOrderResponse` vẫn không chặn được lộ ghi chú nội bộ? Sửa ở tầng nào mới đúng?
7. "Tối đa 5 đơn chờ": vẽ hai request song song khi không khóa dòng tài khoản. Vì sao khóa `customer_account` mà không khóa các phiếu bán?
8. Password spraying khác dò mật khẩu một tài khoản thế nào? Vì sao không giới hạn `refresh` theo IP?
