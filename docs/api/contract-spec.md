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
| SP của NCC | `GET/POST /api/suppliers/{id}/products`, `DELETE /api/suppliers/{id}/products/{productId}` | GET: SP đang liên kết (phân trang, mặc định sort `product.code`). POST `{ productId }` → 201, gán mới hoặc bật lại liên kết đã gỡ; đang liên kết → 409 `DUPLICATE_RESOURCE`; NCC/SP ngừng hoạt động → 409 `BUSINESS_RULE_VIOLATION`. DELETE = gỡ (`active=false`) → 204 |

Danh sách nhận `page`, `size` (tối đa 100), `sort` (ví dụ `sort=name,asc`). `sort` sai thuộc tính → 400.

**Lọc sản phẩm** (`GET /api/products`, thêm 2026-10-16) — tất cả tùy chọn, không gửi = không lọc; lọc trong SQL nên `pagination` phản ánh kết quả đã lọc:

| Tham số | Kiểu | Ý nghĩa |
|---|---|---|
| `keyword` | string | Mã **hoặc** tên chứa từ khóa, không phân biệt hoa thường, tự cắt khoảng trắng hai đầu. `%`, `_` là ký tự thường. Chưa bỏ dấu tiếng Việt (`cam` không khớp `cám`). |
| `categoryId` | number | Thuộc danh mục này. Sai kiểu → 400 `VALIDATION_FAILED` |
| `active` | boolean | `true` đang kinh doanh, `false` ngừng kinh doanh |

Ví dụ: `GET /api/products?keyword=heo&categoryId=1&active=true&page=0&size=20`

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
