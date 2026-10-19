# Thiết kế Database

> ⚠️ **Tài liệu thiết kế.** Chỉ các bảng đánh dấu ✅ là schema đã duyệt; còn lại là ứng viên, cần được xem xét và phê duyệt trước khi tạo migration.

## Các thực thể

| Thực thể | Vai trò | Trạng thái |
|---|---|---|
| `category` | Danh mục sản phẩm (cám gà, cám heo...) | ✅ Đã duyệt 2026-10-14 (V2) |
| `product` | Sản phẩm cám | ✅ Đã duyệt 2026-10-14 (V2) |
| `warehouse` | Kho hàng | ✅ Đã duyệt 2026-10-16 (V3) |
| `supplier` | Nhà cung cấp | ✅ Đã duyệt 2026-10-16 (V4) |
| `product_supplier` | NCC được phép cung cấp SP nào (nhiều–nhiều) | ✅ Đã duyệt 2026-10-16 (V4) |
| `customer` | Khách hàng (đại lý, trang trại, khách lẻ) | ✅ Đã duyệt 2026-10-20 (V8) |
| `employee` | Nhân viên + đơn giá lương ngày / tấn bốc vác | ✅ Đã duyệt 2026-10-20 (V9) |
| `attendance` | Chấm công: nhân viên × ngày (trạng thái ngày + tấn bốc vác) | ✅ Đã duyệt 2026-10-20 — chưa tạo migration |
| `payroll`, `payroll_item` | Bảng lương tháng đã chốt (bản chụp đơn giá) | ✅ Đã duyệt 2026-10-20 — chưa tạo migration |
| `inventory` | Tồn kho hiện tại (product × warehouse) | ✅ Đã duyệt 2026-10-19 (V6) |
| `stock_document` | Chứng từ kho (phiếu nhập, phiếu xuất, phiếu điều chuyển) | ✅ Đã duyệt 2026-10-19 (V6) — mới mở loại `INBOUND` |
| `stock_document_item` | Dòng chi tiết trong chứng từ | ✅ Đã duyệt 2026-10-19 (V6) |
| `stock_transaction` | Lịch sử biến động tồn kho (bất biến) | ✅ Đã duyệt 2026-10-19 (V6) |

> **Mã tự sinh (từ V10):** mọi cột `code` dùng `DEFAULT format_code('<tiền tố>', nextval('<seq>'), <độ rộng>)` — đệm số 0 khi ngắn hơn, giữ nguyên khi dài hơn (`KHO1050`). Không dùng `lpad` trực tiếp: `lpad` **cắt** chuỗi dài hơn độ rộng (`lpad('1050', 3)` = `'105'`) → mã trùng. Bảng mới có mã tự sinh phải dùng `format_code` (test `CodeGenerationPersistenceTest` kiểm mọi cột).

## ER Diagram (ứng viên)

```mermaid
erDiagram
    category {
        bigint id PK
        varchar name UK "unique, không phân biệt hoa thường"
        text description
        boolean active
        timestamptz created_at
        timestamptz updated_at
    }

    product {
        bigint id PK
        varchar code UK "Tự sinh SP000001, không sửa"
        varchar name UK "unique, không phân biệt hoa thường"
        bigint category_id FK "NOT NULL"
        varchar unit "BAG | KG"
        numeric net_weight_kg "NUMERIC(10,3), > 0"
        text description
        boolean active "Còn kinh doanh?"
        timestamptz created_at
        timestamptz updated_at
    }

    warehouse {
        bigint id PK
        varchar code UK "Tự sinh KHO001, không sửa"
        varchar name UK "unique, không phân biệt hoa thường"
        varchar type "CENTRAL | RETAIL"
        varchar address "NOT NULL"
        boolean active "Còn hoạt động?"
        timestamptz created_at
        timestamptz updated_at
    }

    supplier {
        bigint id PK
        varchar code UK "Tự sinh NCC001, không sửa"
        varchar name UK "unique, không phân biệt hoa thường"
        varchar contact_name "NOT NULL"
        varchar phone "nullable"
        varchar email "nullable"
        varchar address "nullable"
        boolean active "Còn hoạt động?"
        timestamptz created_at
        timestamptz updated_at
    }

    product_supplier {
        bigint supplier_id PK,FK
        bigint product_id PK,FK
        boolean active "false = đã gỡ liên kết"
        timestamptz created_at
        timestamptz updated_at
    }

    customer {
        bigint id PK
        varchar code UK "KH0001, tự sinh"
        varchar name "được trùng"
        varchar type "DEALER | FARM | RETAIL"
        varchar phone UK "NOT NULL, đã chuẩn hóa, CHECK ^0[0-9]{9,10}$"
        varchar contact_name "nullable"
        varchar address "nullable"
        varchar note "nullable"
        boolean active
        timestamptz created_at
        timestamptz updated_at
    }

    inventory {
        bigint id PK
        bigint product_id FK "UK (product_id, warehouse_id)"
        bigint warehouse_id FK
        integer quantity "Tồn hiện tại, CHECK >= 0"
        timestamptz created_at
        timestamptz updated_at
    }

    stock_document {
        bigint id PK
        varchar code UK "PN000001 (service sinh từ inbound_code_seq)"
        varchar type "INBOUND (OUTBOUND, TRANSFER mở sau)"
        varchar status "DRAFT | COMPLETED | CANCELLED | REVERSED"
        bigint warehouse_id FK "Kho chính (nhập/xuất)"
        bigint supplier_id FK "bắt buộc khi INBOUND"
        date document_date "Ngày chứng từ, lùi được, không tương lai"
        varchar note
        numeric total_amount "NUMERIC(18,0)"
        varchar reason "Lý do hủy/đảo"
        timestamptz completed_at
        timestamptz cancelled_at
        timestamptz reversed_at
        timestamptz created_at
        timestamptz updated_at
    }

    stock_document_item {
        bigint id PK
        bigint document_id FK "UK (document_id, product_id)"
        bigint product_id FK
        integer ordered_quantity "SL đặt, chỉ phiếu nhập, > 0"
        integer quantity "SL tác động tồn (= SL thực nhận), >= 0"
        numeric unit_price "NUMERIC(15,2), >= 0"
        numeric line_amount "NUMERIC(18,0), HALF_UP về đồng"
        timestamptz created_at
        timestamptz updated_at
    }

    stock_transaction {
        bigint id PK
        bigint document_item_id FK "Tham chiếu dòng chứng từ"
        bigint product_id FK
        bigint warehouse_id FK
        varchar type "IN | OUT"
        integer quantity "Luôn dương"
        integer quantity_before "Tồn trước giao dịch"
        integer quantity_after "Tồn sau giao dịch, CHECK cân"
        bigint reversal_of_id FK "UK, nullable — giao dịch đảo trỏ về gốc"
        timestamptz created_at
    }

    category ||--o{ product : "phân nhóm"
    product ||--o{ inventory : "tồn tại các kho"
    warehouse ||--o{ inventory : "chứa sản phẩm"
    product ||--o{ stock_document_item : "xuất hiện trong"
    stock_document ||--|{ stock_document_item : "chứa"
    warehouse ||--o{ stock_document : "kho chính"
    supplier ||--o{ stock_document : "cung cấp"
    supplier ||--o{ product_supplier : "được phép cung cấp"
    product ||--o{ product_supplier : "do NCC cung cấp"
    customer ||--o{ stock_document : "mua hàng"
    stock_document_item ||--|{ stock_transaction : "tạo giao dịch"
    product ||--o{ stock_transaction : "biến động"
    warehouse ||--o{ stock_transaction : "tại kho"
```

## Giải thích thiết kế

### category & product (✅ đã duyệt — migration V2)

- `id`: `BIGINT GENERATED ALWAYS AS IDENTITY` (chuẩn SQL, cấm chèn ID thủ công).
- `product.code`: `DEFAULT 'SP' || lpad(nextval('product_code_seq')::text, 6, '0')`. Sequence an toàn khi nhiều request đồng thời (khác `MAX(code)+1` bị trùng khi đồng thời). Rollback có thể làm nhảy số — chấp nhận.
- Vì mã tự sinh không chặn được trùng lặp, dùng `UNIQUE INDEX (lower(name))` để chặn tạo trùng sản phẩm.
- `unit`: `CHECK (unit IN ('BAG','KG'))` — danh sách ít đổi, không cần bảng riêng.
- `net_weight_kg NUMERIC(10,3) CHECK > 0` — phục vụ báo cáo quy đổi tấn.
- `category_id NOT NULL` + FK; index `idx_product_category` cho lọc theo danh mục.
- Thời gian dùng `TIMESTAMPTZ` để không lệch khi server chạy UTC.

### warehouse (✅ đã duyệt — migration V3)

- `code`: `DEFAULT 'KHO' || lpad(nextval('warehouse_code_seq')::text, 3, '0')` — cùng cách sinh mã như `product`. Vượt 999 kho thì `lpad` không cắt số, mã thành `KHO1000` (vẫn unique).
- `UNIQUE INDEX (lower(name))` chặn trùng tên (BR-WH-03).
- `type`: `CHECK (type IN ('CENTRAL','RETAIL'))`.
- `address NOT NULL` (BR-WH-01).
- Không xóa cứng (BR-WH-04): sau này `inventory`, `stock_document`, `stock_transaction` sẽ có FK tới kho.

### supplier & product_supplier (✅ đã duyệt — migration V4)

- `supplier.code`: `DEFAULT 'NCC' || lpad(nextval('supplier_code_seq')::text, 3, '0')` — cùng cách sinh mã như `warehouse`.
- `UNIQUE INDEX (lower(name))` chặn trùng tên (BR-SUP-03). Chỉ `contact_name` bắt buộc; `phone`, `email`, `address` nullable (BR-SUP-01). Định dạng email kiểm tra ở API, không ở DB.
- `product_supplier` dùng **khóa chính kép** `(supplier_id, product_id)` thay vì cột `id` riêng: mỗi cặp chỉ có một dòng, gỡ/gán lại chỉ đổi `active` (BR-SUP-05). Phương án thay thế `id` + `UNIQUE(supplier_id, product_id)` chỉ cần khi dòng nối có vòng đời riêng hoặc bảng khác trỏ tới nó.
- `idx_product_supplier_product (product_id)`: khóa chính chỉ phục vụ truy vấn bắt đầu bằng `supplier_id`; index này phục vụ chiều ngược "SP Y do những NCC nào cung cấp".

### Tìm kiếm không dấu (✅ đã duyệt 2026-10-19 — migration V5)

- `CREATE EXTENSION unaccent` + hàm `immutable_unaccent(text)`: bỏ dấu tiếng Việt, xử lý thêm `đ`/`Đ` bằng `replace`. Dùng bởi `CommonSpecifications.keywordContains`: `lower(immutable_unaccent(cột)) LIKE lower(immutable_unaccent(:pattern))`.
- Bọc thành `IMMUTABLE` vì `unaccent()` gốc chỉ `STABLE` → không đánh index được. Hiện **chưa có index** (dữ liệu nhỏ, quét bảng đủ nhanh). Khi một bảng lên hàng chục nghìn dòng: thêm `pg_trgm` + `GIN (lower(immutable_unaccent(name)) gin_trgm_ops)` — `LIKE '%...%'` không dùng được B-tree.
- Triển khai: user chạy migration cần quyền tạo extension (image `postgres` chính thức đã có gói contrib).

### product.min_stock_quantity (✅ đã duyệt 2026-10-19 — migration V7)

- `INTEGER NULL` + `CHECK ck_product_min_stock (min_stock_quantity >= 0)`. NULL = không cảnh báo Sắp hết (BR-STK-03).
- Một mức cho mỗi SP, so với tổng tồn mọi kho. Phương án bị loại: mỗi cặp (SP, kho) một mức đặt ở `inventory`. Cách đó chính xác hơn khi mỗi kho bán một lượng khác nhau, nhưng cần màn hình đặt mức theo kho và phải tạo sẵn dòng tồn = 0.
- Truy vấn tồn theo SP **không cần index mới**: `uk_inventory_product_warehouse` có `product_id` đứng đầu, phục vụ cả `SUM … WHERE product_id IN (…) GROUP BY product_id` lẫn subquery lọc tình trạng. "Đang về" đọc `stock_document_item` theo `product_id` và hiện chưa có index cho cột này (dữ liệu nhỏ); kiểm chứng bằng EXPLAIN ANALYZE ở Tuần 3.

### inventory (Tồn kho) (✅ đã duyệt — migration V6)

- Mỗi bản ghi là cặp **(product_id, warehouse_id)**, `UNIQUE uk_inventory_product_warehouse`.
- `quantity INTEGER` (BR-INV-07) với **`CHECK ck_inventory_quantity_non_negative (quantity >= 0)`** ở DB (BR-INV-03).
- Là **snapshot** tồn hiện tại. JPA chỉ đọc (`@Immutable`); thay đổi duy nhất qua `InventoryRepository`:
  cộng bằng `INSERT … ON CONFLICT DO UPDATE … RETURNING`, trừ bằng `UPDATE … WHERE quantity >= :q RETURNING`.
- Đối chiếu được với `stock_transaction` (`quantity_after` của giao dịch mới nhất = tồn hiện tại).

### stock_document (Chứng từ) (✅ đã duyệt — migration V6)

- Dùng chung cho phiếu nhập / xuất / điều chuyển, phân biệt bằng `type`. V6 chỉ mở `CHECK (type IN ('INBOUND'))`; module xuất/điều chuyển sẽ nới CHECK và thêm cột (`customer_id`, `target_warehouse_id`) bằng migration mới.
- `code`: **không** có DEFAULT ở DB (khác `product`/`supplier`) vì các loại phiếu khác tiền tố (`PN`, `PX`, `PC`). Service gọi `nextval('inbound_code_seq')` rồi format `PN%06d`.
- `status`: `DRAFT → COMPLETED`, `DRAFT → CANCELLED`, `COMPLETED → REVERSED` (BR-IMP-11, 12). CHECK đảm bảo mốc thời gian đi kèm trạng thái (`completed_at` khi COMPLETED/REVERSED, `cancelled_at`, `reversed_at`).
- `ck_stock_document_supplier`: phiếu nhập bắt buộc có NCC.
- `document_date DATE`: ngày chứng từ (lùi được); thời điểm ghi sổ thật là `completed_at`.
- `total_amount NUMERIC(18,0)` = tổng `line_amount`, tính lại mỗi lần lưu.
- Nội dung chỉ đổi khi `DRAFT`, luôn sau khi khóa dòng phiếu `SELECT … FOR UPDATE` (BR-IMP-14). Không xóa ở mọi trạng thái (BR-IMP-13).

### stock_document_item (Dòng chứng từ) (✅ đã duyệt — migration V6)

- `UNIQUE (document_id, product_id)`: mỗi SP một dòng (BR-IMP-A05); index này cũng phục vụ "các dòng của phiếu X".
- `ordered_quantity` (SL đặt, chỉ phiếu nhập, nullable cho loại khác) và `quantity` (SL tác động tồn = SL thực nhận, ≥ 0) — BR-IMP-03.
- `line_amount = quantity × unit_price`, làm tròn `HALF_UP` về đồng (BR-MON-05) — tính trong Java (`StockDocumentItem.lineAmount`) và lưu lại.
- Sửa phiếu nháp cập nhật dòng **tại chỗ theo `product_id`** (không xóa-hết-thêm-lại): giữ id dòng, không phụ thuộc thứ tự Hibernate xếp DELETE/INSERT khi flush (INSERT chạy trước thì xóa rồi thêm lại cùng SP sẽ vi phạm UNIQUE).

### stock_transaction (Giao dịch kho — BẤT BIẾN) (✅ đã duyệt — migration V6)

- Mỗi bản ghi là **một biến động** tồn của **một SP** tại **một kho**, trỏ về dòng chứng từ sinh ra nó.
- `type`: IN / OUT; `quantity` luôn > 0.
- `quantity_before`, `quantity_after` + **`CHECK ck_stock_transaction_balance`**: IN thì after = before + qty, OUT thì after = before − qty.
- `reversal_of_id` (FK tự tham chiếu, **UNIQUE**): giao dịch đảo trỏ về giao dịch gốc; mỗi giao dịch chỉ bị đảo một lần (BR-INV-06, BR-IMP-12).
- **Chặn sửa/xóa ở DB**: trigger `trg_stock_transaction_immutable` (BEFORE UPDATE OR DELETE, FOR EACH ROW) và `trg_stock_transaction_no_truncate` (BEFORE TRUNCATE) → `RAISE EXCEPTION … ERRCODE restrict_violation`. Phía Java thêm `@Immutable`, không setter.
- So với thiết kế ứng viên: **bỏ cột `reason`** (PURCHASE/SALE...) — loại nghiệp vụ suy ra từ `stock_document.type` qua dòng chứng từ; đổi tên `reference_transaction_id` → `reversal_of_id` cho rõ nghĩa.

Ví dụ (khi có module điều chuyển): phiếu điều chuyển 100 bao cám A từ kho K1 sang K2 tạo:
1. StockTransaction: product=A, warehouse=K1, type=OUT, quantity=100
2. StockTransaction: product=A, warehouse=K2, type=IN, quantity=100

## Kiểu dữ liệu cho tiền và số lượng (✅ chốt 2026-10-19)

| Trường | PostgreSQL | Java | Quy tắc |
|---|---|---|---|
| Đơn giá | `NUMERIC(15,2)` | `BigDecimal` | VND, tối đa 2 chữ số lẻ (BR-MON-03, 04) |
| Số lượng | `INTEGER` | `int` / `Integer` | Luôn nguyên, cả BAG lẫn KG (BR-INV-07); JSON `2.5` → 400 (`accept-float-as-int: false`) |
| Thành tiền dòng, tổng phiếu | `NUMERIC(18,0)` | `BigDecimal` | `HALF_UP` về đồng; tổng = cộng các dòng, không làm tròn thêm (BR-MON-05) |

## Chiến lược cập nhật tồn

**Phương án chọn: Snapshot + Transaction Log**

```
inventory.quantity = giá trị hiện tại (đọc nhanh)
stock_transaction   = lịch sử đầy đủ (kiểm tra, đối chiếu)
```

Khi cập nhật tồn:
1. Lock dòng inventory (SELECT ... FOR UPDATE hoặc UPDATE với WHERE quantity >= :amount)
2. Cập nhật `inventory.quantity`
3. Insert `stock_transaction` với `quantity_before` và `quantity_after`
4. Tất cả trong cùng một transaction

**Tại sao không chỉ dùng transaction log?**
- Tính tổng từ hàng triệu bản ghi sẽ chậm khi dữ liệu lớn
- Snapshot cho phép đọc tồn hiện tại trong O(1)

**Đánh đổi:**
- Cần giữ snapshot nhất quán với transaction log
- Có thể xây chức năng kiểm tra nhất quán (so sánh `inventory.quantity` với `SUM` từ `stock_transaction`)

## Xử lý đồng thời

### Ngăn tồn âm

```sql
-- Cách 1: Conditional update (ưu tiên)
UPDATE inventory
SET quantity = quantity - :amount, updated_at = NOW()
WHERE product_id = :productId
  AND warehouse_id = :warehouseId
  AND quantity >= :amount;
-- Nếu affected rows = 0 → không đủ tồn, throw exception

-- Cách 2: SELECT FOR UPDATE + CHECK constraint (backup)
-- Lock dòng trước, kiểm tra, cập nhật
-- CHECK constraint là tuyến phòng thủ cuối cùng
```

### Deadlock khi điều chuyển

Khi 2 điều chuyển đồng thời: K1→K2 và K2→K1 có thể deadlock.

**Giải pháp: Lock ordering** — luôn lock kho có ID nhỏ hơn trước.

```
Transfer K1→K2: lock K1 trước, rồi K2
Transfer K2→K1: lock K1 trước (ID nhỏ hơn), rồi K2
```

Cả hai transaction đều lock theo thứ tự ID tăng dần → không deadlock.

### Đã triển khai ở V6 (phiếu nhập)

| Tình huống | Cơ chế | Test kiểm chứng |
|---|---|---|
| Hai người cùng bấm Hoàn tất / Đảo một phiếu | Khóa dòng `stock_document` bằng `SELECT … FOR UPDATE` (`StockDocumentRepository.findForUpdate`), **rồi** kiểm tra lại trạng thái trong transaction | `InboundReceiptConcurrencyTest`: 5 luồng → đúng 1 thành công. Đã thử bỏ khóa → cả 5 thành công, test fail |
| Hai phiếu cùng nhập SP mới vào cùng kho | `INSERT … ON CONFLICT (product_id, warehouse_id) DO UPDATE` — không lỗi trùng UNIQUE, không mất lần cộng | Chuỗi `quantity_before → after` của 5 giao dịch nối liền |
| Đảo phiếu khi hàng đã xuất bớt | `UPDATE … WHERE quantity >= :q`; 0 dòng → `BusinessException` → rollback mọi dòng đã trừ | Phiếu 2 dòng, dòng 2 thiếu → dòng 1 không bị trừ, không có OUT nào |
| Nhiều dòng cùng phiếu | Xử lý theo `product_id` tăng dần (lock ordering) | — |

## Index dự kiến

| Index | Bảng | Cột | Phục vụ truy vấn |
|---|---|---|---|
| UK | `inventory` | `(product_id, warehouse_id)` | Tìm tồn theo sản phẩm + kho |
| UK | `product` | `code` | Tìm sản phẩm theo mã |
| UK | `warehouse` | `code` | Tìm kho theo mã |
| UK | `stock_document` | `code` | Tìm chứng từ theo mã (V6) |
| UK | `stock_document_item` | `(document_id, product_id)` | Dòng của một phiếu; chặn SP lặp (V6) |
| UK | `stock_transaction` | `reversal_of_id` | Chặn đảo hai lần (V6) |
| IDX | `stock_transaction` | `(product_id, warehouse_id, created_at)` | Lịch sử tồn theo sản phẩm + kho + thời gian (V6) |
| IDX | `stock_transaction` | `document_item_id` | Tìm giao dịch theo dòng chứng từ (V6) |
| IDX | `stock_document` | `(type, document_date)` | Lọc chứng từ theo loại + ngày chứng từ (V6 — đổi từ `created_at` vì danh sách lọc theo ngày chứng từ) |

> ⚠️ Index đã tạo nhưng chưa kiểm chứng bằng EXPLAIN ANALYZE với dữ liệu thực tế (việc của Tuần 3).

## Quyết định còn mở

- ✅ ~~Precision cho tiền?~~ → Đơn giá `NUMERIC(15,2)`, thành tiền `NUMERIC(18,0)` (BR-MON-04, 05)
- ✅ ~~Số lượng có cần thập phân không?~~ → Không, `INTEGER` (BR-INV-07)
- ✅ ~~Quy tắc làm tròn thành tiền?~~ → `HALF_UP` về đồng (BR-MON-05)
- ⚠️ Cần quản lý lô hàng (batch) không? → Tạm **không** trong MVP (BR-IMP-A08)
- ✅ ~~Chứng từ có cần trạng thái?~~ → Có: DRAFT / COMPLETED / CANCELLED / REVERSED (BR-IMP-02, 11, 12)
- ✅ ~~Mã chứng từ tự sinh hay người dùng nhập?~~ → Tự sinh `PN000001` (BR-IMP-07)
- ✅ ~~Cần bảng `category` riêng?~~ → Có (BR-CAT-01)
- ✅ ~~Cần bảng `unit_of_measure` riêng?~~ → Không; `unit` là chuỗi có CHECK, kèm `net_weight_kg` (BR-PRD-05, 06)
