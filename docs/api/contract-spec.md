# Đặc tả hợp đồng API — Grain Warehouse

> Tài liệu quy ước chuẩn giao tiếp giữa Frontend và Backend.
> Mọi API endpoint đều tuân thủ format này. Không tạo response kiểu riêng.

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
        "rejectedValue": -5000,
        "message": "Giá bán không được âm"
      },
      {
        "field": "name",
        "rejectedValue": "",
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
| `INTERNAL_ERROR` | 500 | Lỗi chưa xử lý phía server | — |

### Lưu ý cho Frontend

- **Luôn kiểm tra `success`** trước khi truy cập `data`.
- Khi `success = false`, hiển thị `error.message` cho người dùng.
- Nếu `error.details` có `field`, map lỗi vào ô input tương ứng trên form.
- Cấu hình **Axios interceptor** để xử lý tập trung lỗi 401 (redirect login) và 500 (toast thông báo).

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

interface FieldError {
  field: string;
  rejectedValue: unknown;
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
