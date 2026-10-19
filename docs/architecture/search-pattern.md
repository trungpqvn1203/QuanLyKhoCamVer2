# Quy ước tìm kiếm & lọc danh sách

> ✅ Đã áp dụng (2026-10-19) cho Sản phẩm, Nhà cung cấp, Kho. Module mới có danh sách **bắt buộc** theo khuôn này.
> `SearchConventionTest` (backend) tự kiểm tra phần backend; phần frontend kiểm tra bằng review.

## 1. Tổng quan

```
FE  XxxFilters (type)  ─┐                      BE  XxxSearchCriteria (record, implements HasKeyword/HasActive)
    FILTER_FIELDS      ─┼─ GET /api/xxx?... ──▶     XxxSpecifications.from(criteria)
    FilterBar          ─┤   keyword=&active=&        └─ CommonSpecifications: keywordContains, equal, in,
    useFilteredList    ─┘   page=&size=&sort=            between, instantWithinDates, hasActive
                                                    Repository.findAll(spec, pageable) → PageResponse
```

**Phân vai:** backend quyết định *có những tiêu chí nào* và *mỗi tiêu chí lọc cột nào, so khớp ra sao*.
Frontend chỉ gửi *giá trị* cho các tham số đã có trong [contract-spec](../api/contract-spec.md).
Tham số lạ bị bỏ qua; sai kiểu → 400 `VALIDATION_FAILED`.

## 2. Quy tắc

| # | Quy tắc | Lý do |
|---|---|---|
| R1 | Danh sách dùng `GET` + query string, không dùng `POST .../search` | Đọc dữ liệu: F5, bookmark, cache được. Chỉ xem xét POST khi điều kiện quá dài cho URL |
| R2 | Phân trang/sắp xếp qua `page` (từ 0), `size` (≤ 100), `sort` — **không** đặt trong record tiêu chí | Đã có `Pageable` + `PageResponse` theo contract; không làm trùng |
| R3 | Tên tham số chung: `keyword` (tìm tự do), `active` (trạng thái), khoảng: `xxxFrom` / `xxxTo` | FE dùng lại `FilterBar`, người đọc API đoán được |
| R4 | `XxxSearchCriteria` là **record** trong `<module>/dto`; có `keyword` → `implements HasKeyword`, có `active` → `implements HasActive` | Bất biến, Spring bind thẳng; interface thay cho kế thừa (module chỉ nhận phần mình có) |
| R5 | `XxxSpecifications` trong `<module>/repository`: `final`, constructor `private`, `public static Specification<Xxx> from(XxxSearchCriteria)` | Một chỗ duy nhất ánh xạ tiêu chí → cột |
| R6 | Tên cột luôn viết trong code, **không** nhận tên cột từ request | Không cho lọc cột không có index hoặc không nên lộ |
| R7 | Lọc trong SQL; không lọc trên trình duyệt | `totalElements`, số trang phải đúng trên toàn bộ dữ liệu |
| R8 | Tham số null/rỗng = không lọc điều kiện đó (helper trả `null`) | Request cũ vẫn chạy; gửi thiếu không thành "không khớp gì" |

## 3. Thêm tìm kiếm cho module mới — checklist

Ví dụ: module Khách hàng cần tìm theo tên/SĐT, loại khách, ngày tạo.

### Backend

1. **Record tiêu chí** — `customer/dto/CustomerSearchCriteria.java`:

   ```java
   public record CustomerSearchCriteria(
           String keyword,
           CustomerType type,                                                  // enum: sai giá trị → 400
           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom, // bắt buộc có @DateTimeFormat
           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
           Boolean active) implements HasKeyword, HasActive {
   }
   ```

2. **Specifications** — `customer/repository/CustomerSpecifications.java`:

   ```java
   public final class CustomerSpecifications {
       private CustomerSpecifications() {
       }

       public static Specification<Customer> from(CustomerSearchCriteria criteria) {
           return Specification.allOf(
                   CommonSpecifications.keywordContains(criteria, "code", "name", "phone"),
                   CommonSpecifications.equal("type", criteria.type()),
                   CommonSpecifications.instantWithinDates("createdAt", criteria.createdFrom(), criteria.createdTo()),
                   CommonSpecifications.hasActive(criteria));
       }
   }
   ```

3. **Repository** — `extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer>`.
   Có quan hệ `@ManyToOne` hiện trong danh sách → override `findAll(Specification, Pageable)` với `@EntityGraph` (chống N+1, xem `ProductRepository`).
4. **Service** — `list(CustomerSearchCriteria criteria, Pageable pageable)` →
   `PageResponse.from(repository.findAll(CustomerSpecifications.from(criteria), pageable), CustomerResponse::from)`.
5. **Controller** — `@GetMapping list(@ModelAttribute CustomerSearchCriteria criteria, @PageableDefault(size = 20, sort = "id") Pageable pageable)`.
6. **Test API** (mẫu: `SupplierApiTest`): tìm có dấu/không dấu, theo mã, `%` là ký tự thường, từng điều kiện + kết hợp, sai kiểu → 400.
7. **contract-spec.md** — thêm bảng tham số lọc của module.

### Frontend

1. `customer-api.ts`:

   ```ts
   export type CustomerFilters = {          // dùng `type`, không dùng `interface`
     keyword?: string
     type?: string
     createdFrom?: string                    // 'YYYY-MM-DD'
     createdTo?: string
     active?: boolean
   }
   list: ({ page, size }: PageQuery, filters: CustomerFilters = {}) =>
     apiRequest<PageResponse<Customer>>(withQuery(BASE, { page, size, sort: 'id,asc', ...toFilterParams(filters) })),
   ```

2. Màn hình danh sách:

   ```ts
   const FILTER_FIELDS: FilterField<CustomerFilters>[] = [
     { type: 'keyword', name: 'keyword', placeholder: 'Tìm theo mã, tên hoặc SĐT' },
     { type: 'select', name: 'type', placeholder: 'Loại khách', options: CUSTOMER_TYPE_OPTIONS },
     { type: 'dateRange', from: 'createdFrom', to: 'createdTo', placeholder: ['Tạo từ ngày', 'đến ngày'] },
     { type: 'status', name: 'active', activeLabel: 'Đang hoạt động', inactiveLabel: 'Ngừng hoạt động' },
   ]
   const { page, loading, error, changePage, reload, filters, applyFilters, filtering } = useFilteredList(customerApi.list)
   // <FilterBar fields={FILTER_FIELDS} value={filters} onChange={applyFilters} />
   // <DataTable ... extraReservedHeight={FILTER_BAR_HEIGHT} emptyText={<ListEmpty filtering={filtering} ... />} />
   ```

3. Test API client (mẫu: `supplier-api.test.ts`): không lọc chỉ gửi page/size/sort; có lọc gửi đúng tham số.

### Hoàn tất

- [ ] `mvn verify` (gồm `SearchConventionTest`) · `npm run lint` · `npx tsc --noEmit` · `npm test`
- [ ] contract-spec cập nhật bảng tham số

## 4. Helper có sẵn (`common/search/CommonSpecifications`)

| Helper | Dùng khi | Ghi chú |
|---|---|---|
| `keywordContains(criteria, cột...)` | Ô tìm tự do trên nhiều cột | Không phân biệt hoa thường **và dấu** (migration V5, xử lý cả `đ`); `%`, `_` là ký tự thường |
| `equal("cột", value)` | Chọn một giá trị: enum, id danh mục (`"category.id"`), boolean | Đường dẫn lồng bằng dấu chấm |
| `in("cột", values)` | Chọn nhiều giá trị | Danh sách rỗng = không lọc |
| `between("cột", from, to)` | Khoảng số / `LocalDate` | Hai đầu tính; thiếu một đầu = khoảng hở |
| `instantWithinDates("cột", fromDate, toDate)` | Cột `Instant` (`createdAt`...) lọc theo **ngày** | Theo giờ Việt Nam; ngày cuối tính trọn đến 23:59 |
| `hasActive(criteria)` | Lọc trạng thái | |

**Cần điều kiện mới** (ví dụ "lớn hơn", "khác"): thêm vào `CommonSpecifications` kèm test trong `CommonSpecificationsTest`, không viết riêng trong module — trừ điều kiện chỉ một module dùng.

## 5. Lỗi hay gặp

- **`LocalDate` trong record luôn kèm `@DateTimeFormat(iso = ISO.DATE)`**: định dạng ngày mặc định của Spring phụ thuộc cấu hình/locale; ghi rõ ISO để `?createdFrom=2026-10-01` chắc chắn bind đúng (chưa có module nào dùng — viết test bind khi thêm module đầu tiên).
- **Lọc ngày trên cột `Instant` bằng `between`** → mất bản ghi của ngày cuối và lệch 7 tiếng. Dùng `instantWithinDates`.
- **Khai báo `XxxFilters` bằng `interface`** → không gán được vào `FilterValues` (lỗi TS). Dùng `type`.
- **Truyền hàm tải dữ liệu tạo mới mỗi lần render vào `useFilteredList`** → gọi API liên tục. Truyền `xxxApi.list` (ổn định).
- **Tìm nhanh trên bảng lớn**: `LIKE '%...%'` không dùng được B-tree. Khi bảng lên hàng chục nghìn dòng: `pg_trgm` + GIN trên `lower(immutable_unaccent(cột))` (xem [database.md](database.md)).

## 6. Phương án đã cân nhắc

| Phương án | Lý do không chọn |
|---|---|
| `POST /xxx/get-data` + `BaseSearchRequest` (body JSON) | Biến API đọc thành RPC; mất F5/bookmark/cache; trùng phân trang với `Pageable` |
| Base class `BaseSearchRequest { keyword, active }` cho mọi module | Không dùng được record; module không có `active` vẫn mang field đó. Thay bằng interface nhỏ `HasKeyword`, `HasActive` |
| Client gửi tên cột tùy ý (`?filter[field]=...`) | Lọc được cột không có index / không nên lộ; khó kiểm soát |
| FE tự sinh ô lọc từ metadata của BE | Nhãn, lựa chọn vẫn phải định nghĩa; chỉ đáng làm khi có màn hình người dùng tự dựng điều kiện (báo cáo động) |
