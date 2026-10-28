# Đặc tả hợp đồng API — Grain Warehouse

> Tài liệu quy ước chuẩn giao tiếp giữa Frontend và Backend.
> Mọi API endpoint đều tuân thủ format này. Không tạo response kiểu riêng.
>
> **Field null bị bỏ khỏi JSON:** Backend dùng `@JsonInclude(NON_NULL)`. Response thành công không có `error`; response lỗi không có `data`; `message` chỉ xuất hiện khi có giá trị.

---

## 1. Chuẩn Response thành công (Success Response)

Áp dụng cho mọi API trả về dữ liệu (GET item, POST, PUT):

```json
{
  "success": true,
  "message": "Tạo sản phẩm thành công",
  "data": {
    "id": 1,
    "code": "CAM-HEO-01",
    "name": "Cám heo con 20kg",
    "unitId": 1,
    "unitName": "Bao",
    "price": 320000
  },
  "timestamp": "2026-10-13T23:30:00Z"
}
```

### HTTP Status Codes cho thành công

| Status | Khi nào dùng | Có body? |
|---|---|---|
| `200 OK` | GET, PUT, PATCH thành công | ✅ Có `data` |
| `201 Created` | POST tạo mới resource | ✅ Có `data` (chứa ID vừa tạo) |
| `204 No Content` | DELETE thành công | ❌ Không trả body |

---

## 2. Chuẩn Response phân trang (Paginated Response)

Áp dụng cho tất cả danh sách (sản phẩm, chứng từ, lịch sử kho):

```json
{
  "success": true,
  "data": {
    "items": [
      { "id": 1, "code": "CAM-HEO-01", "name": "Cám heo con 20kg" },
      { "id": 2, "code": "CAM-GA-01", "name": "Cám gà đẻ 25kg" }
    ],
    "pagination": {
      "page": 0,
      "size": 20,
      "totalElements": 142,
      "totalPages": 8,
      "isFirst": true,
      "isLast": false
    }
  },
  "timestamp": "2026-10-13T23:30:00Z"
}
```

### Quy ước Query Parameters phân trang

| Parameter | Kiểu | Mặc định | Mô tả |
|---|---|---|---|
| `page` | int | `0` | Trang hiện tại (0-indexed) |
| `size` | int | `20` | Số phần tử mỗi trang (max: 100) |
| `sort` | string | tùy endpoint | Sắp xếp, VD: `createdAt,desc` |

---

## 3. Chuẩn Response lỗi (Error Response)

Khi có lỗi (4xx, 5xx), Backend bắt qua `@RestControllerAdvice` và trả về:

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "Dữ liệu gửi lên không hợp lệ",
    "details": [
      {
        "field": "price",
        "message": "Giá bán không được âm"
      },
      {
        "field": "name",
        "message": "Tên sản phẩm không được để trống"
      }
    ]
  },
  "timestamp": "2026-10-13T23:30:00Z"
}
```

### Bảng mã lỗi chuẩn (Error Codes)

| Error Code | HTTP Status | Mô tả | Ví dụ |
|---|---|---|---|
| `VALIDATION_FAILED` | 400 | Dữ liệu đầu vào không hợp lệ | Tên trống, giá âm |
| `RESOURCE_NOT_FOUND` | 404 | Không tìm thấy bản ghi | Sản phẩm ID=999 không tồn tại |
| `DUPLICATE_RESOURCE` | 409 | Vi phạm UNIQUE constraint | Mã SKU đã tồn tại |
| `BUSINESS_RULE_VIOLATION` | 409 | Vi phạm quy tắc nghiệp vụ | Xuất vượt tồn kho |
| `UNAUTHORIZED` | 401 | Chưa đăng nhập / Token hết hạn / sai mật khẩu | Access token quá 15 phút |
| `FORBIDDEN` | 403 | Không có quyền, hoặc thiếu / sai CSRF token | Nhân viên bán hàng mở phiếu nhập |
| `REFRESH_RACE` | 409 | Refresh token vừa bị thay bởi tab khác (< 30 giây) — gửi lại request gốc | Hai tab cùng gia hạn |
| `METHOD_NOT_ALLOWED` | 405 | HTTP method không được hỗ trợ | `PATCH /api/products/1` |
| `DATABASE_UNAVAILABLE` | 503 | Không kết nối được DB (chỉ dùng ở `/api/health`) | DB tắt |
| `INTERNAL_ERROR` | 500 | Lỗi chưa xử lý phía server | — |

> `error.message` không bao giờ chứa chi tiết nội bộ (SQL, stack trace, host DB) — chi tiết chỉ ghi vào log backend.
>
> **Giới hạn đã biết:** nếu JSON sai kiểu (ví dụ `unitId: "abc"`), lỗi đọc JSON xảy ra **trước** Bean Validation → `details` chỉ chứa field sai kiểu, chưa liệt kê các field khác (ví dụ thiếu `name`).

### Lưu ý cho Frontend

- **Luôn kiểm tra `success`** trước khi truy cập `data`.
- Khi `success = false`, hiển thị `error.message` cho người dùng.
- Nếu `error.details` có `field`, map lỗi vào ô input tương ứng trên form.
- Xử lý lỗi tập trung trong `src/api/client.ts` (tự viết bằng `fetch`, xem ADR-0002): 401 → thử gia hạn phiên một lần rồi gửi lại, vẫn 401 → chuyển trang đăng nhập; 500 → thông báo chung.

---

## 4. Quy ước Request (FE → BE)

### Naming

- **Request Body & Response:** `camelCase` cho tất cả field names.
- **URL Path:** `kebab-case`, ví dụ: `/api/stock-documents`.
- **Query Parameters:** `camelCase`, ví dụ: `?warehouseId=1&searchKeyword=cam`.

### HTTP Methods

| Method | Mục đích | Request Body | Idempotent |
|---|---|---|---|
| `GET` | Lấy dữ liệu | ❌ | ✅ |
| `POST` | Tạo mới | ✅ | ❌ |
| `PUT` | Cập nhật toàn bộ | ✅ | ✅ |
| `DELETE` | Xóa | ❌ | ✅ |

### URL Pattern

```
GET    /api/products              # Danh sách (có phân trang)
GET    /api/products/:id          # Chi tiết 1 item
POST   /api/products              # Tạo mới
PUT    /api/products/:id          # Cập nhật
DELETE /api/products/:id          # Xóa (soft delete)
```

### Endpoint đã triển khai

| Module | Endpoint | Ghi chú |
|---|---|---|
| Danh mục | `GET/POST /api/categories`, `GET/PUT/DELETE /api/categories/{id}` | DELETE = `active=false`; PUT có `active` để mở lại |
| Đơn vị tính | `GET/POST /api/product-units`, `GET/PUT/DELETE /api/product-units/{id}` | _(Từ 2026-10-28)_ POST `{ name }`, PUT `{ name, active }` (tên ≤ 50 ký tự). Trùng tên (kể cả đã ngừng) → 409 `DUPLICATE_RESOURCE`, đã ngừng thì message gợi ý mở lại. Response `{ id, name, fixedNetWeightKg?, active, createdAt, updatedAt }` — `fixedNetWeightKg` chỉ có ở Kg (= 1), không nhận từ request. DELETE = `active=false`. Ghi: chỉ Quản lý hệ thống |
| Sản phẩm | `GET/POST /api/products`, `GET/PUT/DELETE /api/products/{id}` | Không nhận `code` (tự sinh); response có `categoryName`, `unitId`, `unitName`. Request gửi `unitId` _(từ 2026-10-28; trước đó `unit: "BAG"\|"KG"`)_ và tùy chọn `supplierIds: [id]` (BR-PRD-10; giao diện hiện chỉ gửi tối đa một id, API nhận nhiều để mở rộng sau): POST không gửi = chưa gán NCC; PUT không gửi = **giữ nguyên** liên kết, `[]` = gỡ hết; NCC không tồn tại → 404, NCC ngừng hoạt động (liên kết mới) → 409, SP không được tạo: thiếu → 400 field `unitId`; không tồn tại → 404; chuyển sang đơn vị đã ngừng → 409; đơn vị có khối lượng cố định mà `netWeightKg` khác → 409 `BUSINESS_RULE_VIOLATION` |
| Kho | `GET/POST /api/warehouses`, `GET/PUT/DELETE /api/warehouses/{id}` | Không nhận `code` (tự sinh `KHO001`); `type`: `CENTRAL` \| `RETAIL`; `address` bắt buộc; DELETE = `active=false`; PUT có `active` để mở lại |
| Nhà cung cấp | `GET/POST /api/suppliers`, `GET/PUT/DELETE /api/suppliers/{id}` | Không nhận `code` (tự sinh `NCC001`); chỉ `name` bắt buộc; `taxCode`, `phone`, `email`, `address` tùy chọn (rỗng → bỏ khỏi JSON). `taxCode` bỏ khoảng trắng rồi phải là 10 số hoặc 10 số-3 số, sai → 400 field `taxCode`; trùng NCC khác (kể cả đã ngừng) → 409 `DUPLICATE_RESOURCE`. Lọc `keyword` khớp mã, tên hoặc MST (từ 2026-10-24; trước đó có `contactName` — đã bỏ); DELETE = `active=false`; PUT có `active` để mở lại |
| Khách hàng | `GET/POST /api/customers`, `GET/PUT/DELETE /api/customers/{id}` | Không nhận `code` (tự sinh `KH0001`); bắt buộc `name`, `type` (`DEALER`/`FARM`/`RETAIL`, sai → 400), `phone`; `contactName`, `address`, `note` tùy chọn. `phone` được chuẩn hóa (bỏ khoảng trắng/chấm/gạch), sai định dạng → 400 field `phone`; trùng SĐT khách khác (kể cả đã ngừng) → 409 `DUPLICATE_RESOURCE`. DELETE = `active=false`; PUT có `active` để mở lại |
| Nhân viên | `GET/POST /api/employees`, `GET/PUT/DELETE /api/employees/{id}` | Không nhận `code` (tự sinh `NV001`); `name` bắt buộc; `dailyWage`, `unloadTonRate` (đơn giá tấn bốc xuống), `deliveryTonRate` (đơn giá tấn giao hàng) (≥ 0, 2 chữ số lẻ) — phải có ít nhất một trong ba, thiếu cả ba → 400 field `dailyWage` _(từ 2026-10-26; trước đó chỉ có `tonRate` = nay là `unloadTonRate`)_; PUT không gửi/gửi `null` = bỏ cách tính đó. `phone` tùy chọn, chuẩn hóa như khách, không chặn trùng. Lọc `keyword` (mã/tên/SĐT), `active`. DELETE = `active=false` |
| Chấm công | `GET /api/attendance?month=YYYY-MM`, `PUT/DELETE /api/attendance/{employeeId}/{date}` | GET: bảng công tháng (không gửi `month` = tháng hiện tại giờ VN): `{ month, daysInMonth, today, employees: [{ employeeId, employeeCode, employeeName, active, paysByDay, paysByUnload, paysByDelivery, totalWorkUnits, totalUnloadTons, totalDeliveryTons, days: [{ employeeId, date, status, unloadTons, deliveryTons, note }] }] }` — gồm NV đang làm + NV đã nghỉ có chấm công trong tháng; `days` chỉ có ngày đã chấm. PUT `{ status: FULL\|HALF\|ABSENT, unloadTons?, deliveryTons?, note? }` tạo hoặc ghi đè ô (mỗi loại tấn không gửi = 0, tối đa 3 chữ số lẻ). Ngày tương lai → 400 field `date`; ABSENT mà `unloadTons` hoặc `deliveryTons` > 0 → 400 field tương ứng; NV đã nghỉ → 409 `BUSINESS_RULE_VIOLATION`. DELETE xóa ô → 204, ô chưa chấm → 404 |
| Bảng lương | `GET /api/payrolls/{YYYY-MM}`, `POST …/{YYYY-MM}/finalize`, `POST …/{YYYY-MM}/cancel` | GET: `{ month, finalized, payrollId?, finalizedAt?, totalAmount, items: [{ employeeId, employeeCode, employeeName, employeeActive, workUnits, unloadTons, deliveryTons, dailyWage?, unloadTonRate?, deliveryTonRate?, dayAmount, unloadAmount, deliveryAmount, totalAmount }], history: [{ payrollId, status, totalAmount, finalizedAt, cancelledAt, cancelReason }] }` — chưa chốt = tạm tính với đơn giá hiện tại; đã chốt = bản chụp. Chỉ gồm NV có chấm công trong tháng. `finalize`: tháng tương lai → 400 field `month`; đã chốt / chưa có chấm công → 409. `cancel` `{ reason }` bắt buộc (thiếu → 400); chưa chốt → 409. Tháng đã chốt: PUT/DELETE chấm công → 409; GET bảng công có `payrollFinalized: true` |
| SP của NCC | `GET/POST /api/suppliers/{id}/products`, `DELETE /api/suppliers/{id}/products/{productId}` | GET: SP đang liên kết (phân trang, mặc định sort `product.code`). POST `{ productId }` → 201, gán mới hoặc bật lại liên kết đã gỡ; đang liên kết → 409 `DUPLICATE_RESOURCE`; NCC/SP ngừng hoạt động → 409 `BUSINESS_RULE_VIOLATION`. DELETE = gỡ (`active=false`) → 204 |
| NCC của SP | `GET /api/products/{id}/suppliers` | Chiều ngược: NCC đang liên kết với SP (phân trang, mặc định sort `supplier.code`), item gồm `supplierId`, `supplierCode`, `supplierName`, `taxCode?`, `phone?`, `supplierActive`. Chỉ đọc — gán/gỡ dùng API của NCC ở dòng trên. Code đặt trong module supplier để tránh phụ thuộc vòng |
| Phiếu nhập | `GET/POST /api/inbound-receipts`, `GET/PUT /api/inbound-receipts/{id}`, `POST /api/inbound-receipts/{id}/complete`, `…/cancel`, `…/reverse` | Xem mục **Phiếu nhập kho** bên dưới. Không có DELETE → 405 |
| Phiếu bán | `GET/POST /api/sales-orders`, `GET/PUT /api/sales-orders/{id}`, `POST …/{id}/complete`, `…/cancel`, `…/reverse`, `POST …/{id}/payments`, `POST …/{id}/payments/{paymentId}/reverse` | Xem mục **Phiếu bán, thanh toán, công nợ** bên dưới. Không có DELETE → 405 |
| Giá riêng của khách | `GET /api/customers/{id}/prices`, `PUT/DELETE /api/customers/{id}/prices/{productId}` | Xem mục **Phiếu bán, thanh toán, công nợ** |
| Thông tin cửa hàng | `GET/PUT /api/store-info` | _(Từ 2026-10-28, BR-PRT-01)_ GET (mọi vai trò): `{ configured, name?, address?, phone?, receiptFooter?, updatedAt? }` — chưa cấu hình → `{ configured: false }`. PUT (chỉ Quản lý hệ thống) `{ name, address?, phone?, receiptFooter? }` ghi đè toàn bộ; tên trống → 400 field `name`; ô tùy chọn để trống → lưu NULL, bỏ khỏi JSON |
| Công nợ khách | `GET /api/customer-debts?customerId=` | Xem mục **Phiếu bán, thanh toán, công nợ** |
| Sổ quỹ, phiếu thu/chi | `GET /api/cash-book?account=`, `GET /api/cash-book/summary`, `GET/PUT /api/cash-book/opening`; `GET/POST /api/cash-vouchers`, `POST …/{id}/reverse`; `GET/POST /api/cash-categories`, `PUT …/{id}` | Chỉ `SYSTEM_ADMIN`. Xem mục **Sổ quỹ** bên dưới |
| Kiểm kê kho | `GET/POST /api/stock-counts`, `GET/PUT …/{id}`, `POST …/{id}/refresh-book`, `…/complete`, `…/cancel`, `…/reverse`; `GET/POST /api/stock-count-reasons`, `PUT …/{id}` | Chỉ `SYSTEM_ADMIN`. Xem mục **Kiểm kê kho** bên dưới |
| Công nợ đầu kỳ | `GET/POST /api/opening-customer-debts`, `GET …/{id}`, `POST …/{id}/reverse`, `POST …/{id}/payments`, `POST …/{id}/payments/{paymentId}/reverse`; cùng bộ cho `/api/opening-supplier-debts` | Ghi nhận, đảo: `SYSTEM_ADMIN`. Xem, thu/trả: như công nợ khách / NCC. Xem mục **Công nợ đầu kỳ** bên dưới |
| Tồn kho đầu kỳ | `GET/POST /api/opening-stocks`, `GET/PUT /api/opening-stocks/{id}`, `POST …/{id}/complete`, `POST …/{id}/cancel`, `POST …/{id}/reverse`, `GET /api/opening-stocks/products-with-movement?warehouseId=` | Chỉ `SYSTEM_ADMIN`. Xem mục **Tồn kho đầu kỳ** bên dưới |
| Tạm ứng, trả lương, công nợ nhân viên | `GET/POST /api/employees/{id}/advances`, `POST …/advances/{advanceId}/reverse`; `GET /api/payrolls/{YYYY-MM}/items/{employeeId}`, `POST …/payments`, `POST …/payments/{paymentId}/reverse`; `GET /api/employee-debts?employeeId=` | Chỉ `SYSTEM_ADMIN`. Xem mục **Công nợ nhân viên** bên dưới |
| Trả tiền NCC, công nợ NCC | `POST /api/inbound-receipts/{id}/payments`, `POST …/{id}/payments/{paymentId}/reverse`, `GET /api/supplier-debts?supplierId=` | Xem mục **Trả tiền và công nợ nhà cung cấp** bên dưới |
| Lịch sử biến động tồn | `GET /api/inventory/transactions` | Chỉ đọc (POST → 405). Xem mục **Lịch sử biến động tồn** bên dưới |
| Xác thực | `GET /api/auth/csrf`, `POST /api/auth/login`, `…/refresh`, `…/logout`, `GET /api/auth/me`, `PUT /api/auth/me/password` | Xem mục **Xác thực & phân quyền** bên dưới |
| Người dùng | `GET/POST /api/users`, `GET/PUT /api/users/{id}`, `POST /api/users/{id}/reset-password` | Chỉ `SYSTEM_ADMIN`. Không có DELETE (khóa = `active=false`) |
| Nhập Excel | `GET {base}/import/template`, `POST {base}/import/preview`, `POST {base}/import` với `{base}` = `/api/products`, `/api/customers`, `/api/suppliers`, `/api/employees` | Xem mục **Nhập hàng loạt từ Excel** bên dưới |

**Phiếu nhập kho** (thêm 2026-10-19, BR-IMP-01..14):

| Endpoint | Body | Kết quả / lỗi |
|---|---|---|
| `POST /api/inbound-receipts` | `{ supplierId, warehouseId, documentDate?, note?, lines: [{ productId, orderedQuantity, receivedQuantity?, unitPrice }] }` | 201, phiếu `DRAFT`, chưa đổi tồn. `documentDate` không gửi = hôm nay (giờ VN); ngày tương lai → 400 field `documentDate`. `receivedQuantity` không gửi = `orderedQuantity`. Số lượng là số nguyên (`2.5` → 400). SP lặp dòng → 400 field `lines[i].productId`. SP chưa gán cho NCC, kho/NCC/SP ngừng hoạt động → 409 `BUSINESS_RULE_VIOLATION` |
| `PUT /api/inbound-receipts/{id}` | như POST | Chỉ phiếu `DRAFT`; thay toàn bộ dòng (dòng không gửi bị bỏ). Phiếu trạng thái khác → 409 |
| `POST …/{id}/complete` | — | Chỉ `DRAFT` → `COMPLETED`: tồn tăng theo SL thực nhận, mỗi dòng có SL > 0 ghi một giao dịch IN. Kiểm tra lại kho/NCC/SP/liên kết. Không dòng nào có SL > 0 → 409 |
| `POST …/{id}/cancel` | `{ reason? }` (body tùy chọn) | Chỉ `DRAFT` → `CANCELLED`, không đổi tồn |
| `POST …/{id}/reverse` | `{ reason }` (bắt buộc) | Chỉ `COMPLETED` → `REVERSED`: mỗi giao dịch IN có một OUT trỏ về nó. Tồn không đủ → 409, không đổi gì. Đã đảo rồi → 409 "chỉ được đảo một lần" |

Response chi tiết: `id, code, status, documentDate, warehouseId/Code/Name, supplierId/Code/Name, note, totalAmount, reason, completedAt, cancelledAt, reversedAt, createdAt, updatedAt, lines: [{ id, productId, productCode, productName, unitName, orderedQuantity, receivedQuantity, unitPrice, lineAmount }]` (trường null bị bỏ). `lineAmount = receivedQuantity × unitPrice` làm tròn `HALF_UP` về đồng; `totalAmount` = tổng `lineAmount`. Danh sách trả bản tóm tắt (không có `lines`, `note`, `reason`), mặc định sắp xếp `id` giảm dần.

Lọc `GET /api/inbound-receipts` (tất cả tùy chọn):

| Tham số | Kiểu | Ý nghĩa |
|---|---|---|
| `keyword` | string | Mã phiếu chứa từ khóa, không phân biệt hoa thường (`pn000012` hoặc `12` khớp `PN000012`) |
| `status` | `DRAFT` \| `COMPLETED` \| `CANCELLED` \| `REVERSED` | Sai giá trị → 400 |
| `warehouseId`, `supplierId` | number | Kho nhận / NCC |
| `documentDateFrom`, `documentDateTo` | `YYYY-MM-DD` | Ngày chứng từ trong khoảng, hai đầu đều tính |

**Tồn kho đầu kỳ** (thêm 2026-10-27, BR-OPN-01..10) — chỉ `SYSTEM_ADMIN`:

| Endpoint | Body / tham số | Kết quả |
|---|---|---|
| `GET /api/opening-stocks` | `keyword` (mã phiếu), `status`, `warehouseId`, `documentDateFrom`, `documentDateTo`, phân trang (mặc định id giảm dần) | Trang `{ id, code, status, documentDate, warehouseId, warehouseCode, warehouseName, lineCount, totalQuantity, totalAmount, createdBy?, completedBy?, completedAt?, createdAt }` |
| `GET /api/opening-stocks/{id}` | — | `{ …như trên, note?, reason?, cancelledAt?, reversedAt?, updatedAt, lines: [{ id, productId, productCode, productName, unitName, quantity, unitPrice, lineAmount }] }` |
| `POST /api/opening-stocks` | `{ warehouseId, documentDate?, note?, lines: [{ productId, quantity, unitPrice? }] }` | 201, Nháp, mã `DTK…`. SL ≤ 0 / lẻ → 400 field `lines[i].quantity`; trùng SP → 400 field `lines[i].productId`; không dòng → 400 field `lines`; ngày tương lai → 400 field `documentDate`; kho / SP ngừng → 409; không có → 404. SP đã có biến động vẫn lưu nháp được |
| `PUT /api/opening-stocks/{id}` | như POST | 200. Phiếu không còn Nháp → 409 |
| `POST /api/opening-stocks/{id}/complete` | — | 200, Hoàn tất, tồn tăng. Có SP đã có biến động tại kho → 409 (liệt kê mọi SP vi phạm), phiếu vẫn Nháp; không còn Nháp → 409 |
| `POST /api/opening-stocks/{id}/cancel` | `{ reason? }` (body tùy chọn) | 200, Đã hủy. Không còn Nháp → 409 |
| `POST /api/opening-stocks/{id}/reverse` | `{ reason }` (bắt buộc) | 200, Đã đảo, tồn trừ lại. Không đủ tồn → 409; đã đảo → 409; thiếu lý do → 400 field `reason` |
| `GET /api/opening-stocks/products-with-movement` | `warehouseId` (bắt buộc) | `[productId, …]` — SP đã có biến động tại kho (không tính phiếu đầu kỳ đã đảo). Kho không có → 404 |

**Kiểm kê kho** (thêm 2026-10-27, BR-CNT-01..10) — chỉ `SYSTEM_ADMIN`:

| Endpoint | Body / tham số | Kết quả |
|---|---|---|
| `GET /api/stock-counts` | `keyword` (mã), `status`, `warehouseId`, `documentDateFrom`, `documentDateTo`, phân trang (mặc định id giảm dần) | Trang `{ id, code, status, documentDate, warehouseId, warehouseCode, warehouseName, lineCount, countedLineCount, differenceLineCount, surplusQuantity, shortageQuantity, createdBy?, completedBy?, completedAt?, createdAt }` |
| `GET /api/stock-counts/{id}` | — | `{ …như trên, note?, bookSnapshotAt, reason?, cancelledAt?, reversedAt?, updatedAt, movedSinceSnapshot: [productId] (chỉ phiếu đang kiểm), lines: [{ id, productId, productCode, productName, unitName, productActive, bookQuantity, countedQuantity?, difference?, reasonId?, reasonName?, reasonNote? }] }` |
| `POST /api/stock-counts` | `{ warehouseId, documentDate?, note? }` | 201, phiếu đang kiểm (`DRAFT`) đã nạp mọi SP có tồn > 0. Kho đang có phiếu dở / ngừng hoạt động → 409; ngày tương lai → 400; kho không có → 404 |
| `PUT /api/stock-counts/{id}` | `{ documentDate?, note?, lines: [{ productId, countedQuantity?, reasonId?, reasonNote? }] }` | 200. Thay toàn bộ dòng; SL sổ sách không gửi (dòng cũ giữ số đã chụp, SP mới chụp lúc thêm). SL đếm âm → 400 field `lines[i].countedQuantity`; trùng SP → 400 field `lines[i].productId`; SP / lý do không có → 404; không còn đang kiểm → 409 |
| `POST /api/stock-counts/{id}/refresh-book` | — | 200, chụp lại SL sổ sách mọi dòng, dời `bookSnapshotAt` |
| `POST /api/stock-counts/{id}/complete` | — | 200, ghi chênh vào tồn. Còn dòng chưa đếm / dòng lệch thiếu lý do đang dùng → 409 (liệt kê SP); thiếu mà không đủ tồn → 409, phiếu vẫn đang kiểm |
| `POST /api/stock-counts/{id}/cancel` | `{ reason? }` (body tùy chọn) | 200, `CANCELLED` |
| `POST /api/stock-counts/{id}/reverse` | `{ reason }` | 200, `REVERSED`. Không đủ tồn để trừ phần thừa → 409; đã đảo → 409 |
| `GET /api/stock-count-reasons` | — | `[{ id, name, active, createdAt, updatedAt }]` theo tên |
| `POST /api/stock-count-reasons`, `PUT …/{id}` | `{ name }` / `{ name, active }` | 201 / 200. Trùng tên (không phân biệt hoa thường) → 409 field `name` |

`GET /api/inventory/transactions`: `documentType` có thêm `STOCK_COUNT` (giao dịch IN = kiểm kê thừa, OUT = thiếu).

**Công nợ đầu kỳ** (thêm 2026-10-27, BR-ODB-01..09) — `/api/opening-customer-debts` (khách, mã `DKH…`) và `/api/opening-supplier-debts` (NCC, mã `DNC…`), cùng dạng:

| Endpoint | Body / tham số | Kết quả |
|---|---|---|
| `GET …` | `keyword` (mã hoặc tên khách/NCC), `status` (`COMPLETED`\|`REVERSED`), `partyId`, `unpaidOnly=true`, phân trang (mặc định id giảm dần) | Trang `{ id, code, party (CUSTOMER\|SUPPLIER), status, documentDate, partyId, partyCode, partyName, partyActive, note?, totalAmount, paidAmount, outstandingAmount, reason?, createdBy?, completedAt, reversedAt?, createdAt, updatedAt }` (không kèm `payments`) |
| `GET …/{id}` | — | Như trên + `payments: [...]` cùng dạng lịch sử thu tiền phiếu bán. Id của loại kia → 404 |
| `POST …` | `{ documentDate?, lines: [{ partyId, amount, note? }] }` | 201, **mảng** các khoản vừa tạo (Hoàn tất). Số nợ ≤ 0 / lẻ đồng → 400 field `lines[i].amount`; trùng khách trong lô → 400 field `lines[i].partyId`; lô rỗng → 400 field `lines`; ngày tương lai → 400 field `documentDate`; không có → 404; ngừng hoạt động / đã có nợ đầu kỳ còn hiệu lực → 409 (liệt kê tên). Một dòng lỗi → không ghi dòng nào. Ghi đồng thời trùng khách → 409 `DUPLICATE_RESOURCE` |
| `POST …/{id}/reverse` | `{ reason }` | 200, `REVERSED`. Đã thu/trả > 0 → 409; đã đảo → 409 |
| `POST …/{id}/payments` | `{ paymentDate?, cashAmount?, transferAmount?, note? }` | 201, như thu tiền phiếu bán. Vượt số còn nợ → 409; đã đảo → 409 |
| `POST …/{id}/payments/{paymentId}/reverse` | `{ reason }` | 200. Đã hoàn / là dòng hoàn → 409 |

`GET /api/customer-debts`, `GET /api/supplier-debts`: `outstandingAmount`, `openOrderCount` / `openReceiptCount`, `oldestDocumentDate` tính cả nợ đầu kỳ.

`GET /api/inventory/transactions?documentType=OPENING_STOCK` lọc giao dịch đầu kỳ; `documentType` trong kết quả có thêm giá trị `OPENING_STOCK`.

**Sổ quỹ: tiền mặt, tài khoản, phiếu thu/chi** (thêm 2026-10-26, BR-CASH-01..07) — chỉ `SYSTEM_ADMIN`:

| Endpoint | Body / tham số | Kết quả / lỗi |
|---|---|---|
| `GET /api/cash-book/opening` | — | `{ configured, startDate?, openingCash?, openingBank?, updatedAt?, changes: [{ oldStartDate?, oldOpeningCash?, oldOpeningBank?, newStartDate, newOpeningCash, newOpeningBank, changedBy, changedAt }] }` (lịch sử mới nhất trước) |
| `PUT /api/cash-book/opening` | `{ startDate, openingCash, openingBank }` | 200, như trên. Ngày tương lai → 400 field `startDate`; số âm / lẻ đồng / thiếu → 400 field tương ứng. Mỗi lần gọi thêm một dòng lịch sử |
| `GET /api/cash-book/summary` | — | `{ configured, today, startDate?, cash?, bank? }`, mỗi tài khoản `{ account, balance, todayIn, todayOut, negative }`. Chưa đặt đầu kỳ → `configured: false` (200) |
| `GET /api/cash-book` | `account` (bắt buộc: `CASH`\|`BANK`), `from`, `to` (`YYYY-MM-DD`, mặc định đầu tháng → hôm nay), `source` (tùy chọn: `SALE_PAYMENT`\|`SUPPLIER_PAYMENT`\|`PAYROLL_PAYMENT`\|`EMPLOYEE_ADVANCE`\|`CASH_VOUCHER`) | `{ account, startDate, from, to, source?, openingBalance, totalIn, totalOut, closingBalance, negative, entries: [{ source, sourceId, account, entryDate, amount (có dấu), direction (IN\|OUT), reference?, counterparty?, category?, note?, reversal, reversed, createdAt, balance? }] }`. `from` sớm hơn đầu kỳ → kéo về ngày đầu kỳ. Lọc `source` → tổng thu/chi chỉ của nguồn đó, không có `balance` từng dòng; đầu/cuối khoảng vẫn của cả tài khoản. Chưa đặt đầu kỳ → 409; thiếu `account` → 400 field `account`; `to` < `from` hoặc trước đầu kỳ → 400 field `to`; quá 366 ngày → 400 field `from` |
| `GET /api/cash-vouchers` | `keyword` (mã / người nộp-nhận), `direction`, `account`, `categoryId`, `voucherDateFrom`, `voucherDateTo`, phân trang (mặc định id giảm dần) | Trang `{ id, code, type (VOUCHER\|REVERSAL), direction, account, categoryId, categoryName, amount, voucherDate, counterparty?, note?, reversalOfId?, reversalOfCode?, reversed, createdAt }` |
| `POST /api/cash-vouchers` | `{ direction (IN\|OUT), account, categoryId, amount, voucherDate?, counterparty?, note? }` | 201, mã `PT…` / `PC…`. Chưa đặt đầu kỳ → 409; danh mục khác chiều → 400 field `categoryId`; danh mục ngừng dùng → 409; không có → 404; ngày trước đầu kỳ → 400 field `voucherDate`; số tiền ≤ 0 / lẻ → 400 field `amount` |
| `POST /api/cash-vouchers/{id}/reverse` | `{ reason }` (bắt buộc) | 201, phiếu hoàn mã gốc + `-H`, ngày hôm nay. Đã hoàn / là phiếu hoàn → 409 |
| `GET /api/cash-categories` | — | `[{ id, name, direction, active, createdAt, updatedAt }]` — thu trước, chi sau, theo tên |
| `POST /api/cash-categories` | `{ name, direction }` | 201. Trùng tên (kể cả đã ngừng) → 409 `DUPLICATE_RESOURCE` |
| `PUT /api/cash-categories/{id}` | `{ name, active }` | 200. Không đổi được `direction` |

Thiếu query param bắt buộc (mọi endpoint, thêm 2026-10-26) → 400 `VALIDATION_FAILED`, `details: [{ field: <tên tham số> }]` (trước đây rơi vào 500).

**Công nợ nhân viên: tạm ứng và trả lương** (thêm 2026-10-26, BR-EPAY-01..06) — chỉ `SYSTEM_ADMIN`:

| Endpoint | Body | Kết quả / lỗi |
|---|---|---|
| `GET /api/employees/{id}/advances` | — | `{ employeeId, employeeCode, employeeName, employeeActive, advanceBalance, advances: [{ id, type (ADVANCE\|REVERSAL), method (CASH\|BANK_TRANSFER), amount, advanceDate, note?, reversalOfId?, reversed, createdAt }] }` (mới nhất trước) |
| `POST /api/employees/{id}/advances` | `{ paymentDate?, cashAmount?, transferAmount?, note? }` | 201, trả lại sổ tạm ứng như trên. Cùng quy tắc số tiền như thu tiền phiếu bán (cả hai trống/0 → 400 field `cashAmount`; âm/lẻ → 400). Nhân viên đã nghỉ → 409; không tồn tại → 404. `paymentDate` là ngày ứng |
| `POST /api/employees/{id}/advances/{advanceId}/reverse` | `{ reason }` (bắt buộc) | Dòng `REVERSAL`. Khoản của người khác → 404; đã hoàn / là dòng hoàn → 409; **số dư tạm ứng không đủ** (đã trừ vào lương) → 409 |
| `GET /api/payrolls/{month}/items/{employeeId}` | — | `{ month, employeeId, employeeCode, employeeName, employeeActive, totalAmount, paidAmount, outstandingAmount, tracksDebt, advanceBalance, payments: [{ id, type (PAYMENT\|REVERSAL), method (CASH\|BANK_TRANSFER\|ADVANCE_DEDUCTION), amount, paymentDate, note?, reversalOfId?, reversed, createdAt }] }`. Tháng chưa chốt → 409; nhân viên không có lương tháng đó → 404 |
| `POST /api/payrolls/{month}/items/{employeeId}/payments` | `{ paymentDate?, cashAmount?, transferAmount?, advanceDeduction?, note? }` | 201, trả lại tình trạng dòng lương. Mỗi phần > 0 thành một dòng. Cả ba trống/0 → 400 field `cashAmount`; tổng vượt số còn nợ → 409; `advanceDeduction` vượt số dư tạm ứng → 409; tháng chưa chốt hoặc bảng lương cũ (`tracksDebt = false`) → 409 |
| `POST /api/payrolls/{month}/items/{employeeId}/payments/{paymentId}/reverse` | `{ reason }` (bắt buộc) | Hoàn một khoản trả; hoàn `ADVANCE_DEDUCTION` thì `advanceBalance` cộng lại. Khoản của dòng lương khác → 404; đã hoàn / là dòng hoàn → 409 |
| `POST /api/payrolls/{month}/cancel` | `{ reason }` | **Thêm từ 2026-10-26:** bảng lương đã trả lương cho ít nhất một nhân viên → 409, phải hoàn các khoản trả trước |
| `GET /api/employee-debts?employeeId=` | — | `[{ employeeId, employeeCode, employeeName, employeeActive, unpaidSalaryAmount, advanceBalance, netAmount, unpaidMonths: [{ month, totalAmount, paidAmount, outstandingAmount }] }]` — nhân viên còn lương chưa trả hoặc còn dư tạm ứng, lương chưa trả nhiều nhất lên đầu, không phân trang. `netAmount = unpaidSalaryAmount − advanceBalance` |

`GET /api/payrolls/{month}` (bảng lương) có thêm `tracksDebt` ở cấp bảng và `paidAmount`, `outstandingAmount` ở từng dòng (tạm tính: luôn 0). `GET /api/employees` trả thêm `advanceBalance`.

**Trả tiền và công nợ nhà cung cấp** (thêm 2026-10-25, BR-SPAY-01..06) — quyền: quản lý kho + quản lý hệ thống:

| Endpoint | Body | Kết quả / lỗi |
|---|---|---|
| `POST /api/inbound-receipts/{id}/payments` | `{ paymentDate?, cashAmount?, transferAmount?, note? }` | 201, trả lại chi tiết phiếu nhập. Giống thu tiền phiếu bán: chỉ phiếu `COMPLETED`; cả hai trống/0 → 400 field `cashAmount`; âm/lẻ → 400; vượt số còn nợ → 409. Phiếu cũ `tracksDebt = false` → 409 |
| `POST /api/inbound-receipts/{id}/payments/{paymentId}/reverse` | `{ reason }` (bắt buộc) | Dòng `REVERSAL` cùng hình thức, cùng số tiền, ngày = hôm nay. Khoản của phiếu khác → 404; đã hoàn / là dòng hoàn → 409 |
| `POST /api/inbound-receipts/{id}/reverse` | `{ reason }` | **Thêm từ 2026-10-25:** đã trả tiền (`paidAmount > 0`) → 409, phải hoàn các khoản trả trước |
| `GET /api/supplier-debts?supplierId=` | — | `[{ supplierId, supplierCode, supplierName, supplierTaxCode?, supplierPhone?, supplierActive, outstandingAmount, openReceiptCount, oldestDocumentDate }]` — chỉ NCC còn nợ, nợ nhiều nhất lên đầu, không phân trang |

Response chi tiết phiếu nhập có thêm `paidAmount`, `outstandingAmount`, `tracksDebt`, `payments: [...]` (cùng dạng `payments` của phiếu bán); bản tóm tắt trong danh sách có thêm `paidAmount`, `outstandingAmount`, `tracksDebt`. `outstandingAmount` = `totalAmount − paidAmount` chỉ khi `COMPLETED` và `tracksDebt`, còn lại `0`. Lọc `GET /api/inbound-receipts` thêm `unpaidOnly=true` (phiếu Hoàn tất còn nợ NCC).

**Phiếu bán, thanh toán, công nợ** (thêm 2026-10-20, BR-SAL-01..15):

| Endpoint | Body | Kết quả / lỗi |
|---|---|---|
| `POST /api/sales-orders` | `{ customerId, warehouseId, documentDate?, note?, lines: [{ productId, quantity, unitPrice }] }` | 201, phiếu `DRAFT` mã `PX…`, chưa đổi tồn, không giữ chỗ tồn. Thiếu `customerId` → 400. `quantity` nguyên > 0. Ngày tương lai → 400 field `documentDate`. SP lặp dòng → 400 field `lines[i].productId`. Khách/kho/SP ngừng hoạt động → 409 |
| `PUT /api/sales-orders/{id}` | như POST | Chỉ `DRAFT`; thay toàn bộ dòng |
| `POST …/{id}/complete` | — | Chỉ `DRAFT` → `COMPLETED`: trừ tồn mọi dòng, mỗi dòng một giao dịch OUT. Thiếu tồn ở bất kỳ dòng nào → 409 (nêu SP và tồn hiện tại), **không trừ dòng nào** |
| `POST …/{id}/cancel` | `{ reason? }` | Chỉ `DRAFT` → `CANCELLED` |
| `POST …/{id}/reverse` | `{ reason }` (bắt buộc) | Chỉ `COMPLETED` → `REVERSED`: mỗi OUT có một IN trỏ về nó. **Đã thu tiền (`paidAmount > 0`) → 409**, phải hoàn các khoản thu trước. Đã đảo → 409 |
| `POST …/{id}/payments` | `{ paymentDate?, cashAmount?, transferAmount?, note? }` | 201. Chỉ phiếu `COMPLETED`. Mỗi phần > 0 thành một dòng (`CASH` / `BANK_TRANSFER`). Cả hai trống/0 → 400 field `cashAmount`; số âm hoặc có phần lẻ → 400 field tương ứng; `paymentDate` không gửi = hôm nay, chọn ngày nào cũng được. Tổng vượt số còn nợ → 409 |
| `POST …/{id}/payments/{paymentId}/reverse` | `{ reason }` (bắt buộc) | Ghi dòng `REVERSAL` cùng hình thức, cùng số tiền, ngày = hôm nay. Khoản thu của phiếu khác → 404; đã hoàn / là dòng hoàn → 409 |

Response chi tiết phiếu bán: `id, code, status, documentDate, warehouseId/Code/Name, customerId/Code/Name, customerPhone, note, totalAmount, paidAmount, outstandingAmount, totalTons, reason, completedAt, cancelledAt, reversedAt, createdAt, updatedAt, lines: [{ id, productId, productCode, productName, unitName, quantity, unitPrice, lineAmount, tons }], payments: [{ id, type (PAYMENT|REVERSAL), method (CASH|BANK_TRANSFER), amount, paymentDate, note, reversalOfId?, reversed, createdAt }]`. `outstandingAmount` = `totalAmount − paidAmount` khi `COMPLETED`, còn lại luôn `0` (nháp/hủy/đảo không phát sinh nợ). Mọi hành động (complete, reverse, payments…) trả lại response này. Danh sách trả bản tóm tắt (không `lines`, `payments`, `note`, `reason`, `customerPhone`, `totalTons`), mặc định `id` giảm dần. `tons` = SL × khối lượng tịnh / 1000, làm tròn 3 chữ số lẻ; `totalTons` = cộng `tons` các dòng (in phiếu xuất kho, BR-PRT-03; thêm 2026-10-28).

Lọc `GET /api/sales-orders`: `keyword` (mã phiếu), `status`, `warehouseId`, `customerId`, `documentDateFrom`, `documentDateTo` (như phiếu nhập) + `unpaidOnly=true` (chỉ phiếu `COMPLETED` còn nợ).

| Endpoint | Body | Kết quả / lỗi |
|---|---|---|
| `GET /api/customers/{id}/prices` | — | `[{ productId, productCode, productName, unitName, productActive, unitPrice, updatedAt }]` sắp theo mã SP (không phân trang). Khách không tồn tại → 404 |
| `PUT /api/customers/{id}/prices/{productId}` | `{ unitPrice }` (≥ 0, 2 chữ số lẻ) | 200, đặt hoặc ghi đè giá; trả lại **cả bảng giá** của khách. Khách/SP không tồn tại → 404 |
| `DELETE /api/customers/{id}/prices/{productId}` | — | 204. Chưa có giá riêng → 404 |
| `GET /api/customer-debts` | `?customerId=` tùy chọn | `[{ customerId, customerCode, customerName, customerType, customerPhone, customerActive, outstandingAmount, openOrderCount, oldestDocumentDate }]` — chỉ khách còn nợ, nợ nhiều nhất trước (không phân trang) |

**Tồn kho theo sản phẩm** (thêm 2026-10-19, BR-STK-01..06) — chỉ đọc; đặt trong module inventory để không tạo phụ thuộc vòng product ↔ inventory:

| Endpoint | Kết quả |
|---|---|
| `GET /api/inventory/products` | Trang SP, mỗi item = **đủ trường của `/api/products`** + `stockQuantity` (tổng mọi kho, kể cả kho ngừng), `stockTons`, `incomingQuantity` (SL đặt trên phiếu nhập Nháp), `stockStatus` (`IN_STOCK` \| `LOW` \| `OUT`, theo tổng). Có `warehouseId` thì thêm `warehouseId`, `warehouseStockQuantity`, `warehouseStockTons`, `warehouseIncomingQuantity` |
| `GET /api/inventory/products/{productId}` | `productId, productCode, productName, unitName, netWeightKg, minStockQuantity?, stockQuantity, stockTons, incomingQuantity, stockStatus, warehouses: [{ warehouseId, warehouseCode, warehouseName, warehouseActive, quantity, tons, incomingQuantity, updatedAt? }]`. Chỉ liệt kê kho có tồn > 0 hoặc có hàng đang về, sắp theo mã kho. SP không tồn tại → 404 |

Tham số lọc của danh sách: `keyword`, `categoryId`, `active` (giống `/api/products`) + `warehouseId` (không bớt SP, chỉ thêm số tại kho đó) + `stockStatus` (lọc trong SQL; sai giá trị → 400). `sort` nhận thuộc tính của sản phẩm, chưa sắp theo số tồn.

**Lịch sử biến động tồn** (thêm 2026-10-20, BR-INV-04..06) — đọc `stock_transaction`, mặc định `id` giảm dần (mới ghi sổ trước; không sắp theo `createdAt` vì các dòng của một lần hoàn tất phiếu trùng thời điểm):

| Tham số | Kiểu | Ý nghĩa |
|---|---|---|
| `productId`, `warehouseId` | number | SP / kho |
| `type` | `IN` \| `OUT` | Chiều tăng/giảm; sai giá trị → 400 |
| `documentType` | `INBOUND` \| `OUTBOUND` | Loại chứng từ gây ra biến động; sai giá trị → 400 |
| `keyword` | string | Mã chứng từ chứa từ khóa, không phân biệt hoa thường |
| `createdFrom`, `createdTo` | `YYYY-MM-DD` | Ngày **ghi sổ** (lúc hoàn tất / đảo phiếu) theo giờ VN, hai đầu đều tính — không phải ngày chứng từ |

Item: `id, createdAt, type, quantity, quantityBefore, quantityAfter, reversalOfId?, productId, productCode, productName, unitName, warehouseId, warehouseCode, warehouseName, documentId, documentCode, documentType, documentDate, supplierId?, supplierName?, customerId?, customerName?`. `quantityBefore/After` là tồn **tại kho của dòng đó**. `reversalOfId` có giá trị = giao dịch đảo. Lọc một SP + một kho: `quantityAfter` dòng đầu = tồn hiện tại, `quantityBefore` mỗi dòng = `quantityAfter` dòng ngay sau.

**Xác thực & phân quyền** (thêm 2026-10-20, [ADR-0003](../decisions/ADR-0003-authentication.md), BR-AUTH-01..08):

- **Token không bao giờ nằm trong body JSON.** Backend đặt 2 cookie HttpOnly + Secure + SameSite=Strict: `GW_AT` (access JWT 15 phút, `Path=/api`) và `GW_RT` (refresh token, `Path=/api/auth`). Frontend chỉ cần gọi `fetch` cùng tên miền; trình duyệt tự gửi cookie.
- **CSRF:** mọi `POST/PUT/DELETE` (kể cả đăng nhập) phải có header `X-XSRF-TOKEN` = giá trị cookie `XSRF-TOKEN`; thiếu hoặc sai → 403 `FORBIDDEN`.
- **Phân quyền:** chưa đăng nhập / access token hết hạn → 401 `UNAUTHORIZED`. Không đủ vai trò → 403 `FORBIDDEN`. Ma trận ở ADR-0003 mục 5. Công khai: `/api/health`, `/actuator/health`, 4 endpoint `csrf`/`login`/`refresh`/`logout`.
- **Frontend:** gặp 401 thì gọi `POST /api/auth/refresh` **một lần** (gom chung cho mọi request đang chờ) rồi gửi lại request gốc. Gia hạn thất bại → về `/login?redirect=…`.

| Endpoint | Body | Kết quả / lỗi |
|---|---|---|
| `GET /api/auth/csrf` | — | 204, đặt cookie `XSRF-TOKEN` (JS đọc được) |
| `POST /api/auth/login` | `{ username, password }` | 200 + 2 cookie + `{ id, username, fullName, roles }`. Sai tên **hoặc** mật khẩu → 401 "Sai tên đăng nhập hoặc mật khẩu"; đúng mật khẩu nhưng bị khóa → 401 "Tài khoản đã bị khóa…"; thiếu trường → 400 |
| `POST /api/auth/refresh` | — (cookie `GW_RT`) | 200 + cookie mới + người dùng (refresh token cũ bị thay). Token vừa bị thay < 30 giây (tab khác gia hạn trước) → **409 `REFRESH_RACE`**, không đổi cookie, frontend gửi lại request gốc. Không hợp lệ / hết hạn / bị thu hồi / dùng lại token cũ sau 30 giây (thu hồi cả phiên) → 401 + xóa cookie |
| `POST /api/auth/logout` | — | 204, thu hồi phiên của thiết bị này, xóa cookie. Không có cookie vẫn 204 |
| `GET /api/auth/me` | — | `{ id, username, fullName, roles }` |
| `PUT /api/auth/me/password` | `{ currentPassword, newPassword }` | 204; các thiết bị khác phải đăng nhập lại. Sai mật khẩu cũ → 400 field `currentPassword`; mật khẩu mới < 8 ký tự / > 72 byte / trùng mật khẩu cũ → 400 field `newPassword` |
| `POST /api/users` | `{ username, fullName, password, roles: [...] }` | 201. Tên đăng nhập được cắt khoảng trắng, đổi chữ thường; sai định dạng → 400 field `username`; trùng → 409 `DUPLICATE_RESOURCE`; `roles` rỗng → 400; vai trò lạ → 400 |
| `PUT /api/users/{id}` | `{ fullName, roles, active }` | 200. Đổi vai trò / khóa → thu hồi mọi phiên người đó. Khóa / bỏ vai trò Quản lý hệ thống của người cuối cùng → 409; tự khóa mình → 409 |
| `POST /api/users/{id}/reset-password` | `{ newPassword }` | 204, thu hồi mọi phiên người đó |

Response người dùng: `id, username, fullName, roles (sắp theo thứ tự WAREHOUSE_MANAGER, SALES_STAFF, SYSTEM_ADMIN), active, lastLoginAt?, createdAt, updatedAt` — không bao giờ có mật khẩu. Lọc `GET /api/users`: `keyword` (tên đăng nhập / họ tên), `role`, `active`; mặc định sắp theo `username`.

`/api/products` (POST/PUT) có thêm `minStockQuantity` (số nguyên ≥ 0, tùy chọn). PUT không gửi trường này = bỏ mức tối thiểu.

Danh sách nhận `page`, `size` (tối đa 100), `sort` (ví dụ `sort=name,asc`). `sort` sai thuộc tính → 400.

**Lọc sản phẩm** (`GET /api/products`, thêm 2026-10-16) — tất cả tùy chọn, không gửi = không lọc; lọc trong SQL nên `pagination` phản ánh kết quả đã lọc:

| Tham số | Kiểu | Ý nghĩa |
|---|---|---|
| `keyword` | string | Mã **hoặc** tên chứa từ khóa, không phân biệt hoa thường, **không phân biệt dấu** (`cam` khớp `Cám`, `dong` khớp `Đồng` — từ 2026-10-19, migration V5), tự cắt khoảng trắng hai đầu. `%`, `_` là ký tự thường. |
| `categoryId` | number | Thuộc danh mục này. Sai kiểu → 400 `VALIDATION_FAILED` |
| `active` | boolean | `true` đang kinh doanh, `false` ngừng kinh doanh |

Ví dụ: `GET /api/products?keyword=heo&categoryId=1&active=true&page=0&size=20`

**Lọc nhà cung cấp, kho** (`GET /api/suppliers`, `GET /api/warehouses`, thêm 2026-10-19) — cùng quy ước với lọc sản phẩm, dùng chung `CommonSpecifications` ở backend.

> **Quy ước tìm kiếm (2026-10-19):** danh sách dùng `GET` + query string, không dùng `POST .../search` — đọc dữ liệu thì F5, bookmark, cache được. Backend bind tham số lọc vào record `XxxSearchCriteria` (`@ModelAttribute`), phân trang/sắp xếp vẫn qua `page`, `size`, `sort`. Chỉ xem xét `POST` khi điều kiện lọc quá dài cho URL (ví dụ danh sách hàng trăm id). Chi tiết & checklist module mới: [search-pattern.md](../architecture/search-pattern.md).


| Tham số | Kiểu | Ý nghĩa |
|---|---|---|
| `keyword` | string | Mã **hoặc** tên chứa từ khóa — cùng cách so khớp với `keyword` của sản phẩm |
| `active` | boolean | `true` đang hoạt động, `false` ngừng hoạt động. Sai kiểu → 400 `VALIDATION_FAILED` |

Ví dụ: `GET /api/suppliers?keyword=cám&active=true&page=0&size=20`

**Lọc khách hàng** (`GET /api/customers`, thêm 2026-10-20) — như trên, thêm: `keyword` khớp mã, tên **hoặc SĐT** (SĐT lưu dạng chỉ có chữ số, nên gõ `0901 234` sẽ không khớp — gõ liền); `type` = `DEALER`/`FARM`/`RETAIL` (sai → 400).

Ví dụ: `GET /api/customers?keyword=0901&type=FARM&active=true`


### Nhập hàng loạt từ Excel (thêm 2026-10-24, BR-XLS)

Bốn loại dữ liệu dùng chung một bộ 3 endpoint. Quyền giống quyền tạo tay (`POST {base}`); tải mẫu theo quyền xem.

| Endpoint | Request | Response |
|---|---|---|
| `GET {base}/import/template` | — | **Không phải JSON**: file `.xlsx` (`Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, `Content-Disposition: attachment; filename="mau-nhap-san-pham.xlsx"`) |
| `POST {base}/import/preview` | `multipart/form-data`, part `file` | 200 `{ totalRows, validRows, errors: [{ row, field?, column?, message }] }`. **Không ghi DB.** Lỗi từng dòng nằm trong `data.errors`, không phải envelope lỗi |
| `POST {base}/import` | `multipart/form-data`, part `file` | 201 `{ importedCount }`, message `"Đã nhập N sản phẩm"`. Có dòng lỗi → 400 `VALIDATION_FAILED`, `message` = `"File có N lỗi, chưa nhập dòng nào…"`, `details[].field` = `rows[12].phone` (lỗi cả dòng: `rows[12]`). **Không dòng nào được ghi** |

- `row` là **số dòng trong Excel** (dòng tiêu đề là 1), không phải chỉ số mảng. `field` là tên field của request tạo tay (`name`, `phone`, `categoryId`…); thiếu `field` nghĩa là lỗi cả dòng. `column` là tiêu đề cột để hiển thị.
- Lỗi ở mức file (không phải `.xlsx`, sai mẫu hoặc mẫu cũ, đổi tiêu đề cột, file chưa có dòng nào, quá 1.000 dòng, quá 2 MB, thiếu part `file`) → 400 `VALIDATION_FAILED`, `details: [{ field: "file", message }]`. Áp dụng cho cả `preview` lẫn `import`.
- Bước `import` kiểm tra lại từ đầu, không dựa vào kết quả `preview` trước đó (dữ liệu có thể đã đổi). Nếu hai người nhập trùng cùng lúc thì UNIQUE ở DB chặn → 409, toàn bộ file được rollback.
- Cột của từng mẫu: SP `Tên sản phẩm*, Danh mục* (theo tên), Đơn vị* (tên đơn vị đang dùng; lỗi ở field `unitId`), Khối lượng tịnh (kg)*, Tồn tối thiểu, Nhà cung cấp (dropdown `mã · tên` NCC đang hoạt động, một NCC, chỉ đọc phần mã; lỗi ở field `supplierId`; mẫu `PRODUCT_v2` từ 2026-10-28), Mô tả`. KH `Tên khách hàng*, Loại khách hàng* (Đại lý/Trang trại/Bán lẻ), Số điện thoại*, Người liên hệ, Địa chỉ, Ghi chú`. NCC (mẫu `SUPPLIER_v2`, từ 2026-10-24) `Tên nhà cung cấp*, Mã số thuế, Số điện thoại, Email, Địa chỉ`. NV (mẫu `EMPLOYEE_v2`, từ 2026-10-26) `Tên nhân viên*, Số điện thoại, Lương ngày (đồng), Đơn giá tấn bốc xuống (đồng/tấn), Đơn giá tấn giao hàng (đồng/tấn)` (phải có ít nhất một trong ba loại lương).

---

## 5. Triển khai phía Backend (Java)

### Generic ApiResponse

```java
// Tất cả Controller trả về ApiResponse<T>
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private ErrorInfo error;
    private Instant timestamp;
}
```

### Global Exception Handler

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    // Bắt MethodArgumentNotValidException → VALIDATION_FAILED
    // Bắt EntityNotFoundException           → RESOURCE_NOT_FOUND
    // Bắt DataIntegrityViolationException   → DUPLICATE_RESOURCE
    // Bắt BusinessException                 → BUSINESS_RULE_VIOLATION
    // Bắt Exception                         → INTERNAL_ERROR
}
```

> ⚠️ Code trên là **thiết kế**, chưa phải code thực thi. Sẽ triển khai khi bắt đầu module Product.

---

## 6. Triển khai phía Frontend (TypeScript)

### Type definitions

```typescript
interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data?: T;
  error?: ApiError;
  timestamp: string;
}

interface ApiError {
  code: string;
  message: string;
  details?: FieldError[];
}

// Không kèm rejectedValue để tránh trả lại dữ liệu nhạy cảm (ví dụ mật khẩu)
interface FieldError {
  field: string;
  message: string;
}

interface PaginatedData<T> {
  items: T[];
  pagination: PaginationInfo;
}

interface PaginationInfo {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  isFirst: boolean;
  isLast: boolean;
}
```

### Axios interceptor pattern

```typescript
// Cấu hình tập trung: auto-handle 401, 500
// Map field errors vào form state
// Toast notification cho lỗi chung
```

> ⚠️ Code trên là **thiết kế**, chưa phải code thực thi.
