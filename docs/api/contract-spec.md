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
    "unit": "BAG",
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
| `UNAUTHORIZED` | 401 | Chưa đăng nhập / Token hết hạn | — |
| `FORBIDDEN` | 403 | Không có quyền | — |
| `METHOD_NOT_ALLOWED` | 405 | HTTP method không được hỗ trợ | `PATCH /api/products/1` |
| `DATABASE_UNAVAILABLE` | 503 | Không kết nối được DB (chỉ dùng ở `/api/health`) | DB tắt |
| `INTERNAL_ERROR` | 500 | Lỗi chưa xử lý phía server | — |

> `error.message` không bao giờ chứa chi tiết nội bộ (SQL, stack trace, host DB) — chi tiết chỉ ghi vào log backend.
>
> **Giới hạn đã biết:** nếu JSON sai kiểu (ví dụ `unit: "TON"`), lỗi đọc JSON xảy ra **trước** Bean Validation → `details` chỉ chứa field sai kiểu, chưa liệt kê các field khác (ví dụ thiếu `name`).

### Lưu ý cho Frontend

- **Luôn kiểm tra `success`** trước khi truy cập `data`.
- Khi `success = false`, hiển thị `error.message` cho người dùng.
- Nếu `error.details` có `field`, map lỗi vào ô input tương ứng trên form.
- Xử lý lỗi tập trung trong `src/api/client.ts` (tự viết bằng `fetch`, xem ADR-0002): 401 → chuyển trang đăng nhập, 500 → thông báo chung.

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
| Sản phẩm | `GET/POST /api/products`, `GET/PUT/DELETE /api/products/{id}` | Không nhận `code` (tự sinh); response có `categoryName` |
| Kho | `GET/POST /api/warehouses`, `GET/PUT/DELETE /api/warehouses/{id}` | Không nhận `code` (tự sinh `KHO001`); `type`: `CENTRAL` \| `RETAIL`; `address` bắt buộc; DELETE = `active=false`; PUT có `active` để mở lại |
| Nhà cung cấp | `GET/POST /api/suppliers`, `GET/PUT/DELETE /api/suppliers/{id}` | Không nhận `code` (tự sinh `NCC001`); `contactName` bắt buộc; `phone`, `email`, `address` tùy chọn (rỗng → bỏ khỏi JSON); DELETE = `active=false`; PUT có `active` để mở lại |
| Khách hàng | `GET/POST /api/customers`, `GET/PUT/DELETE /api/customers/{id}` | Không nhận `code` (tự sinh `KH0001`); bắt buộc `name`, `type` (`DEALER`/`FARM`/`RETAIL`, sai → 400), `phone`; `contactName`, `address`, `note` tùy chọn. `phone` được chuẩn hóa (bỏ khoảng trắng/chấm/gạch), sai định dạng → 400 field `phone`; trùng SĐT khách khác (kể cả đã ngừng) → 409 `DUPLICATE_RESOURCE`. DELETE = `active=false`; PUT có `active` để mở lại |
| Nhân viên | `GET/POST /api/employees`, `GET/PUT/DELETE /api/employees/{id}` | Không nhận `code` (tự sinh `NV001`); `name` bắt buộc; `dailyWage`, `tonRate` (≥ 0, 2 chữ số lẻ) — phải có ít nhất một, thiếu cả hai → 400 field `dailyWage`; PUT không gửi/gửi `null` = bỏ cách tính đó. `phone` tùy chọn, chuẩn hóa như khách, không chặn trùng. Lọc `keyword` (mã/tên/SĐT), `active`. DELETE = `active=false` |
| Chấm công | `GET /api/attendance?month=YYYY-MM`, `PUT/DELETE /api/attendance/{employeeId}/{date}` | GET: bảng công tháng (không gửi `month` = tháng hiện tại giờ VN): `{ month, daysInMonth, today, employees: [{ employeeId, employeeCode, employeeName, active, paysByDay, paysByTon, totalWorkUnits, totalTons, days: [{ employeeId, date, status, tons, note }] }] }` — gồm NV đang làm + NV đã nghỉ có chấm công trong tháng; `days` chỉ có ngày đã chấm. PUT `{ status: FULL\|HALF\|ABSENT, tons?, note? }` tạo hoặc ghi đè ô (`tons` không gửi = 0, tối đa 3 chữ số lẻ). Ngày tương lai → 400 field `date`; ABSENT mà tons > 0 → 400 field `tons`; NV đã nghỉ → 409 `BUSINESS_RULE_VIOLATION`. DELETE xóa ô → 204, ô chưa chấm → 404 |
| Bảng lương | `GET /api/payrolls/{YYYY-MM}`, `POST …/{YYYY-MM}/finalize`, `POST …/{YYYY-MM}/cancel` | GET: `{ month, finalized, payrollId?, finalizedAt?, totalAmount, items: [{ employeeId, employeeCode, employeeName, employeeActive, workUnits, tons, dailyWage?, tonRate?, dayAmount, tonAmount, totalAmount }], history: [{ payrollId, status, totalAmount, finalizedAt, cancelledAt, cancelReason }] }` — chưa chốt = tạm tính với đơn giá hiện tại; đã chốt = bản chụp. Chỉ gồm NV có chấm công trong tháng. `finalize`: tháng tương lai → 400 field `month`; đã chốt / chưa có chấm công → 409. `cancel` `{ reason }` bắt buộc (thiếu → 400); chưa chốt → 409. Tháng đã chốt: PUT/DELETE chấm công → 409; GET bảng công có `payrollFinalized: true` |
| SP của NCC | `GET/POST /api/suppliers/{id}/products`, `DELETE /api/suppliers/{id}/products/{productId}` | GET: SP đang liên kết (phân trang, mặc định sort `product.code`). POST `{ productId }` → 201, gán mới hoặc bật lại liên kết đã gỡ; đang liên kết → 409 `DUPLICATE_RESOURCE`; NCC/SP ngừng hoạt động → 409 `BUSINESS_RULE_VIOLATION`. DELETE = gỡ (`active=false`) → 204 |
| NCC của SP | `GET /api/products/{id}/suppliers` | Chiều ngược: NCC đang liên kết với SP (phân trang, mặc định sort `supplier.code`), item gồm `supplierId`, `supplierCode`, `supplierName`, `contactName`, `phone`, `supplierActive`. Chỉ đọc — gán/gỡ dùng API của NCC ở dòng trên. Code đặt trong module supplier để tránh phụ thuộc vòng |
| Phiếu nhập | `GET/POST /api/inbound-receipts`, `GET/PUT /api/inbound-receipts/{id}`, `POST /api/inbound-receipts/{id}/complete`, `…/cancel`, `…/reverse` | Xem mục **Phiếu nhập kho** bên dưới. Không có DELETE → 405 |
| Phiếu bán | `GET/POST /api/sales-orders`, `GET/PUT /api/sales-orders/{id}`, `POST …/{id}/complete`, `…/cancel`, `…/reverse`, `POST …/{id}/payments`, `POST …/{id}/payments/{paymentId}/reverse` | Xem mục **Phiếu bán, thanh toán, công nợ** bên dưới. Không có DELETE → 405 |
| Giá riêng của khách | `GET /api/customers/{id}/prices`, `PUT/DELETE /api/customers/{id}/prices/{productId}` | Xem mục **Phiếu bán, thanh toán, công nợ** |
| Công nợ khách | `GET /api/customer-debts?customerId=` | Xem mục **Phiếu bán, thanh toán, công nợ** |

**Phiếu nhập kho** (thêm 2026-10-19, BR-IMP-01..14):

| Endpoint | Body | Kết quả / lỗi |
|---|---|---|
| `POST /api/inbound-receipts` | `{ supplierId, warehouseId, documentDate?, note?, lines: [{ productId, orderedQuantity, receivedQuantity?, unitPrice }] }` | 201, phiếu `DRAFT`, chưa đổi tồn. `documentDate` không gửi = hôm nay (giờ VN); ngày tương lai → 400 field `documentDate`. `receivedQuantity` không gửi = `orderedQuantity`. Số lượng là số nguyên (`2.5` → 400). SP lặp dòng → 400 field `lines[i].productId`. SP chưa gán cho NCC, kho/NCC/SP ngừng hoạt động → 409 `BUSINESS_RULE_VIOLATION` |
| `PUT /api/inbound-receipts/{id}` | như POST | Chỉ phiếu `DRAFT`; thay toàn bộ dòng (dòng không gửi bị bỏ). Phiếu trạng thái khác → 409 |
| `POST …/{id}/complete` | — | Chỉ `DRAFT` → `COMPLETED`: tồn tăng theo SL thực nhận, mỗi dòng có SL > 0 ghi một giao dịch IN. Kiểm tra lại kho/NCC/SP/liên kết. Không dòng nào có SL > 0 → 409 |
| `POST …/{id}/cancel` | `{ reason? }` (body tùy chọn) | Chỉ `DRAFT` → `CANCELLED`, không đổi tồn |
| `POST …/{id}/reverse` | `{ reason }` (bắt buộc) | Chỉ `COMPLETED` → `REVERSED`: mỗi giao dịch IN có một OUT trỏ về nó. Tồn không đủ → 409, không đổi gì. Đã đảo rồi → 409 "chỉ được đảo một lần" |

Response chi tiết: `id, code, status, documentDate, warehouseId/Code/Name, supplierId/Code/Name, note, totalAmount, reason, completedAt, cancelledAt, reversedAt, createdAt, updatedAt, lines: [{ id, productId, productCode, productName, unit, orderedQuantity, receivedQuantity, unitPrice, lineAmount }]` (trường null bị bỏ). `lineAmount = receivedQuantity × unitPrice` làm tròn `HALF_UP` về đồng; `totalAmount` = tổng `lineAmount`. Danh sách trả bản tóm tắt (không có `lines`, `note`, `reason`), mặc định sắp xếp `id` giảm dần.

Lọc `GET /api/inbound-receipts` (tất cả tùy chọn):

| Tham số | Kiểu | Ý nghĩa |
|---|---|---|
| `keyword` | string | Mã phiếu chứa từ khóa, không phân biệt hoa thường (`pn000012` hoặc `12` khớp `PN000012`) |
| `status` | `DRAFT` \| `COMPLETED` \| `CANCELLED` \| `REVERSED` | Sai giá trị → 400 |
| `warehouseId`, `supplierId` | number | Kho nhận / NCC |
| `documentDateFrom`, `documentDateTo` | `YYYY-MM-DD` | Ngày chứng từ trong khoảng, hai đầu đều tính |

**Phiếu bán, thanh toán, công nợ** (thêm 2026-10-20, BR-SAL-01..15):

| Endpoint | Body | Kết quả / lỗi |
|---|---|---|
| `POST /api/sales-orders` | `{ customerId, warehouseId, documentDate?, note?, lines: [{ productId, quantity, unitPrice }] }` | 201, phiếu `DRAFT` mã `PX…`, chưa đổi tồn, không giữ chỗ tồn. Thiếu `customerId` → 400. `quantity` nguyên > 0. Ngày tương lai → 400 field `documentDate`. SP lặp dòng → 400 field `lines[i].productId`. Khách/kho/SP ngừng hoạt động → 409 |
| `PUT /api/sales-orders/{id}` | như POST | Chỉ `DRAFT`; thay toàn bộ dòng |
| `POST …/{id}/complete` | — | Chỉ `DRAFT` → `COMPLETED`: trừ tồn mọi dòng, mỗi dòng một giao dịch OUT. Thiếu tồn ở bất kỳ dòng nào → 409 (nêu SP và tồn hiện tại), **không trừ dòng nào** |
| `POST …/{id}/cancel` | `{ reason? }` | Chỉ `DRAFT` → `CANCELLED` |
| `POST …/{id}/reverse` | `{ reason }` (bắt buộc) | Chỉ `COMPLETED` → `REVERSED`: mỗi OUT có một IN trỏ về nó. **Đã thu tiền (`paidAmount > 0`) → 409**, phải hoàn các khoản thu trước. Đã đảo → 409 |
| `POST …/{id}/payments` | `{ paymentDate?, cashAmount?, transferAmount?, note? }` | 201. Chỉ phiếu `COMPLETED`. Mỗi phần > 0 thành một dòng (`CASH` / `BANK_TRANSFER`). Cả hai trống/0 → 400 field `cashAmount`; số âm hoặc có phần lẻ → 400 field tương ứng; `paymentDate` tương lai hoặc trước ngày phiếu → 400 field `paymentDate`. Tổng vượt số còn nợ → 409 |
| `POST …/{id}/payments/{paymentId}/reverse` | `{ reason }` (bắt buộc) | Ghi dòng `REVERSAL` cùng hình thức, cùng số tiền, ngày = hôm nay. Khoản thu của phiếu khác → 404; đã hoàn / là dòng hoàn → 409 |

Response chi tiết phiếu bán: `id, code, status, documentDate, warehouseId/Code/Name, customerId/Code/Name, customerPhone, note, totalAmount, paidAmount, outstandingAmount, reason, completedAt, cancelledAt, reversedAt, createdAt, updatedAt, lines: [{ id, productId, productCode, productName, unit, quantity, unitPrice, lineAmount }], payments: [{ id, type (PAYMENT|REVERSAL), method (CASH|BANK_TRANSFER), amount, paymentDate, note, reversalOfId?, reversed, createdAt }]`. Mọi hành động (complete, reverse, payments…) trả lại response này. Danh sách trả bản tóm tắt (không `lines`, `payments`, `note`, `reason`, `customerPhone`), mặc định `id` giảm dần.

Lọc `GET /api/sales-orders`: `keyword` (mã phiếu), `status`, `warehouseId`, `customerId`, `documentDateFrom`, `documentDateTo` (như phiếu nhập) + `unpaidOnly=true` (chỉ phiếu `COMPLETED` còn nợ).

| Endpoint | Body | Kết quả / lỗi |
|---|---|---|
| `GET /api/customers/{id}/prices` | — | `[{ productId, productCode, productName, unit, productActive, unitPrice, updatedAt }]` sắp theo mã SP (không phân trang). Khách không tồn tại → 404 |
| `PUT /api/customers/{id}/prices/{productId}` | `{ unitPrice }` (≥ 0, 2 chữ số lẻ) | 200, đặt hoặc ghi đè giá; trả lại **cả bảng giá** của khách. Khách/SP không tồn tại → 404 |
| `DELETE /api/customers/{id}/prices/{productId}` | — | 204. Chưa có giá riêng → 404 |
| `GET /api/customer-debts` | `?customerId=` tùy chọn | `[{ customerId, customerCode, customerName, customerType, customerPhone, customerActive, outstandingAmount, openOrderCount, oldestDocumentDate }]` — chỉ khách còn nợ, nợ nhiều nhất trước (không phân trang) |

**Tồn kho theo sản phẩm** (thêm 2026-10-19, BR-STK-01..06) — chỉ đọc; đặt trong module inventory để không tạo phụ thuộc vòng product ↔ inventory:

| Endpoint | Kết quả |
|---|---|
| `GET /api/inventory/products` | Trang SP, mỗi item = **đủ trường của `/api/products`** + `stockQuantity` (tổng mọi kho, kể cả kho ngừng), `stockTons`, `incomingQuantity` (SL đặt trên phiếu nhập Nháp), `stockStatus` (`IN_STOCK` \| `LOW` \| `OUT`, theo tổng). Có `warehouseId` thì thêm `warehouseId`, `warehouseStockQuantity`, `warehouseStockTons`, `warehouseIncomingQuantity` |
| `GET /api/inventory/products/{productId}` | `productId, productCode, productName, unit, netWeightKg, minStockQuantity?, stockQuantity, stockTons, incomingQuantity, stockStatus, warehouses: [{ warehouseId, warehouseCode, warehouseName, warehouseActive, quantity, tons, incomingQuantity, updatedAt? }]`. Chỉ liệt kê kho có tồn > 0 hoặc có hàng đang về, sắp theo mã kho. SP không tồn tại → 404 |

Tham số lọc của danh sách: `keyword`, `categoryId`, `active` (giống `/api/products`) + `warehouseId` (không bớt SP, chỉ thêm số tại kho đó) + `stockStatus` (lọc trong SQL; sai giá trị → 400). `sort` nhận thuộc tính của sản phẩm, chưa sắp theo số tồn.

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
