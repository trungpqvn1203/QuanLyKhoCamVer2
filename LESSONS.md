# LESSONS — Bài học tích lũy

Ghi dồn sau mỗi module, ôn tập ở cuối dự án. Mỗi mục: **khái niệm → ví dụ trong dự án → vì sao quan trọng**.
`NOTES.md` vẫn là sổ tay riêng của bạn; file này do AI ghi để không bỏ sót kiến thức.

---

## Module: Nhập kho — giao diện (2026-10-19)

- **Giao diện chỉ gợi ý, backend mới quyết định** — nút "Hoàn tất" chỉ hiện khi phiếu đang Nháp, nhưng đó là để người dùng đỡ bấm nhầm, không phải lớp bảo vệ. Ví dụ: anh A mở PN000005 lúc còn Nháp, trong lúc đó chị B hoàn tất. Anh A bấm "Hoàn tất" → backend trả 409 (khóa + kiểm tra lại trạng thái) → khung hiện lỗi. Không bao giờ dựa vào việc "ẩn nút" để giữ quy tắc nghiệp vụ.
- **`Form.List` + `Table`** — mỗi dòng hàng là một hàng của `Table`, mỗi ô là một `Form.Item` có tên dạng mảng `[chỉ số dòng, 'orderedQuantity']`. Toàn bộ giá trị form có dạng `{ lines: [{ productId, orderedQuantity, ... }] }`, khớp thẳng với body API.
- **Tên field lỗi phải đổi sang đường dẫn** — backend báo lỗi ở `lines[1].productId` (chuỗi). Form của Ant Design tìm ô theo mảng `['lines', 1, 'productId']`. Truyền nguyên chuỗi thì lỗi không hiện ở đâu cả. Đây là lỗi im lặng, rất khó phát hiện nếu không viết test cho `toFieldPath`.
- **Số thực trong JavaScript** — `7 * 0.15` ra `1.0499999…`, nên làm tròn có thể sai 1 đồng. Tính bằng "xu" (đơn giá × 100 rồi `Math.round`) thì 15 × 7 = 105 xu, luôn làm tròn đúng lên 1 đ. Số trên form vẫn chỉ là số tạm; số chính thức là `BigDecimal` của backend.
- **Ẩn lỗi bằng cách "sửa hộ" người dùng là nguy hiểm** — `InputNumber precision={0}` âm thầm biến 2,5 bao thành 3 bao, giống lỗi `accept-float-as-int` của Jackson ở backend. Báo lỗi "phải là số nguyên" thì người dùng tự quyết.
- **"Hôm nay" ở trình duyệt cũng phải theo giờ VN** — `new Date().toISOString()` là ngày UTC, nên trước 7h sáng ở VN vẫn ra hôm qua. `todayIso()` dùng `Intl` với `timeZone: 'Asia/Ho_Chi_Minh'`.
- **Suy ra state thay vì đồng bộ bằng effect** — lưu `{ supplierId, items }` của lần tải gần nhất thay vì hai state `linkedProducts` + `loading` được set trong effect. "Đang tải" = NCC đang chọn khác NCC của kết quả. Ít trạng thái hơn thì ít khả năng lệch nhau hơn (lint `set-state-in-effect` đã nhắc).
- **`key` để reset cả component** — mỗi lần mở khung phiếu, trang danh sách tăng `key` → React tạo `InboundReceiptDrawer` mới, mọi state (phiếu đã tải, lỗi, đang lưu) bắt đầu sạch, không cần viết code dọn.

### Câu hỏi ôn tập (để cuối dự án)

1. Nếu chỉ ẩn nút "Hoàn tất" ở frontend mà backend không khóa + kiểm tra trạng thái, có những cách nào để phiếu vẫn bị hoàn tất hai lần?
2. Vì sao ô sản phẩm lấy từ "SP đang gán cho NCC" thay vì tìm trong toàn bộ SP? Khi nào phải đổi cách làm?
3. "Lưu & hoàn tất" gọi 2 API liên tiếp (tạo nháp → hoàn tất). Nếu API thứ hai lỗi thì dữ liệu ở trạng thái nào, và giao diện cần hiện gì?

---

## Module: Nhập kho — backend (2026-10-19)

### Trạng thái phiếu & khóa

- **Kiểm tra trạng thái phải nằm SAU khóa, trong cùng transaction** — Anh A và chị B cùng mở phiếu PN000005 (Nháp, 100 bao cám heo) và cùng bấm "Hoàn tất". Nếu service đọc phiếu bằng `findById`, cả hai đều thấy `DRAFT` → cả hai cộng 100 bao → kho báo 200 bao trong khi chỉ nhận 100. Với `SELECT … FOR UPDATE` (`findForUpdate`), B phải **chờ** A commit, rồi đọc lại thấy `COMPLETED` → bị từ chối 409.
  _Đã kiểm chứng:_ tạm đổi `findForUpdate` thành `findById` → test 5 luồng cho **5/5 thành công** (tồn cộng 5 lần) → test fail. Khôi phục → 1/5.
- **Khóa chỉ có tác dụng khi mọi đường đều đi qua nó** — khóa của PostgreSQL không chặn ai *đọc thường*; nó chỉ bắt các transaction *cũng xin khóa* phải xếp hàng. Vì vậy `InboundReceiptService` có đúng một cửa `lockForChange(id, trạng thái cần, hành động)` cho sửa / hoàn tất / hủy / đảo. Thêm thao tác mới mà quên đi qua cửa này = lỗ hổng mới.
- **Pessimistic vs optimistic lock** — *Pessimistic* (`FOR UPDATE`): khóa trước, người đến sau chờ. Hợp khi xung đột gây hậu quả thật (cộng tồn hai lần). *Optimistic* (`@Version`): không khóa, lúc ghi so phiên bản, lệch thì báo lỗi để người dùng thử lại. Hợp với form sửa thông tin NCC (hiếm khi hai người sửa cùng lúc, sai thì sửa lại).
- **Persistence context có thể trả dữ liệu cũ** — trong cùng transaction, nếu phiếu đã được nạp trước đó, Hibernate trả lại *object trong bộ nhớ* dù câu SQL có `FOR UPDATE`. Vì vậy `lockForChange` phải là lần đọc phiếu đầu tiên trong transaction.
- **Đổi trạng thái bằng hành động, không bằng `PUT status`** — `POST /{id}/complete`, `/cancel`, `/reverse`: mỗi hành động có điều kiện và tác động tồn riêng. Nếu cho `PUT { status: "COMPLETED" }`, server phải đoán ý định từ chênh lệch dữ liệu.

### Cộng / trừ tồn nguyên tử

- **Lost update** — "đọc tồn 100 → Java tính 100 − 10 → ghi 90" chạy song song hai lần cho ra 90 thay vì 80. Phải để DB làm trong **một câu**: `UPDATE … SET quantity = quantity - :q WHERE … AND quantity >= :q RETURNING quantity`. Rỗng = không đủ tồn → `BusinessException` → rollback.
- **Upsert `INSERT … ON CONFLICT DO UPDATE`** — lần đầu nhập "Cám heo con 25kg" vào Kho Hà Nội thì chưa có dòng tồn. Hai phiếu hoàn tất cùng lúc mà dùng "kiểm tra có chưa → chưa thì INSERT" thì cả hai cùng INSERT → lỗi trùng UNIQUE. `ON CONFLICT` để PostgreSQL tự xử lý: một bên tạo, bên kia cộng vào. Test kiểm tra chuỗi `quantity_before → quantity_after` của 5 giao dịch nối liền nhau (không mất lần cộng nào).
- **`RETURNING` thay cho SELECT lại** — lấy tồn *sau khi đổi* ngay trong câu UPDATE để ghi `quantity_before/after`. SELECT riêng sau đó có thể thấy giá trị đã bị transaction khác đổi.
- **`Propagation.MANDATORY`** — `InventoryService` bắt buộc chạy *bên trong* transaction của nghiệp vụ gọi tới. Nếu nó tự mở transaction riêng (`REQUIRES_NEW`), phần cộng tồn của dòng 1 có thể đã commit trong khi dòng 2 lỗi → vi phạm "tất cả hoặc không có gì".
- **Lock ordering** — các dòng luôn được xử lý theo `product_id` tăng dần. Hai phiếu cùng chứa cám heo + cám gà sẽ khóa theo cùng thứ tự, nên không bao giờ rơi vào cảnh "A giữ heo chờ gà, B giữ gà chờ heo" (deadlock).

### Lịch sử bất biến — phòng thủ nhiều lớp

- `StockTransaction` không có setter → `@Immutable` (Hibernate không bao giờ sinh UPDATE) → **trigger DB** chặn `UPDATE`/`DELETE`/`TRUNCATE`, kể cả khi ai đó sửa tay bằng psql. Sai sót sửa bằng giao dịch đảo: phiếu PN000005 đảo → mỗi giao dịch IN có một OUT với `reversal_of_id` trỏ về nó. `UNIQUE (reversal_of_id)` bảo đảm không đảo hai lần, kể cả khi service có bug.
- **CHECK "giao dịch cân"** — `IN ⇒ after = before + qty`: DB tự bắt lỗi tính sai snapshot.
- Trigger `FOR EACH ROW` **không chạy với `TRUNCATE`** → cần thêm trigger `FOR EACH STATEMENT` riêng.

### Tiền & số lượng

- **HALF_UP vs HALF_EVEN** — 3 bao × 12.345,50 đ = 37.036,50 đ: HALF_UP → 37.037; HALF_EVEN (làm tròn ngân hàng) → 37.036. Test dùng các ca `.5` (0,5 → 1; 2,5 → 3) để nếu ai đổi `RoundingMode` thì test fail ngay.
- **Jackson âm thầm cắt số lẻ** — mặc định `"orderedQuantity": 2.5` vào field `Integer` thành `2` bao mà không báo lỗi. Tắt bằng `spring.jackson.deserialization.accept-float-as-int: false` → 400. Đây là kiểu lỗi chỉ phát hiện được khi viết test cho đúng quy tắc nghiệp vụ.
- **"Hôm nay" theo giờ Việt Nam** — server chạy UTC, lúc 0h30 sáng ở VN thì UTC vẫn là hôm qua. Lúc đó `@PastOrPresent` (theo múi giờ JVM) sẽ chặn nhầm ngày hôm nay. Kiểm tra trong service bằng `LocalDate.now(BUSINESS_ZONE)`.

### JPA

- **Sửa dòng tại chỗ, không xóa-hết-thêm-lại** — Hibernate không chạy SQL theo thứ tự bạn gọi mà xếp lại lúc flush: với `em.remove()`, DELETE chạy **sau** INSERT; riêng orphan removal (bỏ phần tử khỏi list) thì lại được xếp trước. Nếu "xóa dòng cám heo, thêm lại dòng cám heo" mà INSERT chạy trước DELETE → vi phạm `UNIQUE (document_id, product_id)`. Khớp theo `productId` (có thì cập nhật, mới thì thêm, thiếu thì bỏ) tránh hẳn câu hỏi thứ tự đó, đồng thời giữ id dòng. _(Thứ tự flush ở đây là kiến thức về Hibernate, chưa được kiểm chứng bằng test trong dự án.)_
- **Native query có `RETURNING` không dùng `@Modifying`** — `@Modifying` chỉ trả số dòng bị ảnh hưởng.

### Test

- **Test `@Transactional` không thấy được rollback của service** — service join vào transaction của test, nên phần đã trừ ở dòng 1 vẫn còn trong cùng transaction cho tới cuối test. Ca "đảo phiếu 2 dòng, dòng 2 thiếu tồn → dòng 1 không bị trừ" và các ca đa luồng phải ở lớp **không** `@Transactional` (`InboundReceiptConcurrencyTest`), commit thật, rồi tự dọn ở `@AfterEach`.
- **Dọn bảng bất biến trong DB test** — `ALTER TABLE … DISABLE TRIGGER` ngay trong transaction dọn dẹp: nếu lỗi giữa chừng thì rollback, trigger tự bật lại (DDL của PostgreSQL có transaction). Không bao giờ làm việc này với dữ liệu thật.
- **Đồng thời thật sự** — `CountDownLatch` làm "vạch xuất phát": mọi luồng sẵn sàng rồi mới cùng chạy, tăng khả năng chồng lấn.
- **Thử phá code để kiểm chứng test** — test đạt ngay lần đầu chưa chứng minh được gì. Cố ý bỏ khóa và thấy test fail mới biết test thực sự bảo vệ quy tắc.

### Công cụ / môi trường

- **IDE ghi đè `target/classes` bằng class hỏng** — sửa file bằng `sed` khi VS Code đang mở: trình biên dịch ECJ của IDE build lại, gặp lỗi giả "Flexible Constructor Bodies" (Lombok) và ghi ra class chứa `Unresolved compilation problems`. Maven thấy class mới hơn source nên không biên dịch lại → mọi test lỗi "Constructor threw exception". Dùng `mvn clean test` / `mvn clean verify` khi kết quả khó hiểu.

### Câu hỏi ôn tập (để cuối dự án)

1. Nếu `lockForChange` kiểm tra trạng thái *trước* rồi mới gọi `findForUpdate`, chuyện gì xảy ra khi hai người cùng bấm "Hoàn tất"?
2. Vì sao `InventoryService` dùng `Propagation.MANDATORY` thay vì `REQUIRED` hay `REQUIRES_NEW`?
3. Đảo phiếu khi tồn không đủ bị chặn ở mấy lớp? Lớp nào vẫn chặn được nếu ai đó xóa câu `WHERE quantity >= :q`?
4. Vì sao ca test rollback giữa chừng không viết được trong lớp test có `@Transactional`?
5. `ON CONFLICT DO UPDATE` giải quyết vấn đề gì mà "SELECT xem có chưa → INSERT" không giải quyết được?

---

## Module: Bộ lọc dùng chung + rà soát UI/UX danh sách (2026-10-19)

### Backend

- **Specification dùng chung, tên cột do code quyết định** — `CommonSpecifications.keywordContains(keyword, "code", "name")` dùng lại được cho SP, NCC, Kho. Tên thuộc tính được **viết cứng trong service**, không lấy từ request: nếu cho client gửi `?filter[field]=...` tùy ý, người dùng có thể lọc cột không có index (chậm) hoặc cột không nên lộ.
- **Lọc trong SQL, không lọc trên trình duyệt** — lọc 20 dòng đang xem thì `totalElements` và số trang sai. Ví dụ: 45 NCC, lọc "Ngừng hoạt động" trên trang 1 chỉ thấy 2 dòng, nhưng thực tế có 7 NCC ngừng nằm rải ở trang 2, 3.
- **Tìm không dấu bằng `unaccent` (V5)** — gõ "cam heo" ra "Cám heo con 25kg". Hai vế (cột và từ khóa) đều chuẩn hóa **trong SQL** bằng cùng hàm `lower(immutable_unaccent(...))`, tránh lệch giữa cách Java và PostgreSQL bỏ dấu.
- **`IMMUTABLE` vs `STABLE`** — PostgreSQL chỉ cho đánh index trên biểu thức dùng hàm `IMMUTABLE` (cùng đầu vào luôn cùng kết quả). `unaccent()` là `STABLE` vì từ điển có thể đổi → bọc lại, chỉ định rõ từ điển. `LIKE '%cam%'` (có `%` ở đầu) không dùng được B-tree; khi cần nhanh phải dùng `pg_trgm` + GIN.
- **Tiêu chí lọc là record, bind bằng `@ModelAttribute`** — `GET /api/suppliers?keyword=cam&active=true` → `SupplierSearchCriteria(keyword, active)`. Thêm tiêu chí = thêm field vào record + một dòng ở `XxxSpecifications.from`. Vẫn giữ GET: tìm kiếm là đọc dữ liệu; `POST /get-data` biến API thành RPC, mất F5/bookmark/cache.
- **Interface nhỏ thay cho base class (composition over inheritance)** — record không `extends` được; `BaseSearchRequest { keyword, active }` buộc mọi module mang `active` kể cả lịch sử giao dịch (không có trạng thái). `record SupplierSearchCriteria(...) implements HasKeyword, HasActive` chỉ nhận đúng phần mình có, và `CommonSpecifications.hasActive(criteria)` nhận thẳng interface.
- **Test giữ quy ước (convention test)** — `SearchConventionTest` quét classpath bằng `ClassPathScanningCandidateComponentProvider` của Spring (không thêm ArchUnit): `*SearchCriteria` phải là record, có `XxxSpecifications.from(...)`. Đã thử cố ý bỏ `HasActive` khỏi `WarehouseSearchCriteria` → test fail đúng chỗ. Kèm một test "phải tìm thấy 3 module" để tránh pass rỗng khi quét sai package.
- **Lọc ngày trên cột `Instant`** — "đến 15/10" phải gồm cả 23:59 ngày 15/10 giờ VN; 00:00 ngày 15/10 giờ VN là 17:00 ngày 14/10 UTC. `instantWithinDates` dùng `>= đầu ngày from` và `< đầu ngày (to + 1)`, không dùng `BETWEEN`.
- **Không đổi envelope chỉ vì "chuẩn khác"** — đề xuất `ApiResponse {statusCode, ...}` và `PagedResponse {pageIndex từ 1, ...}` trùng chức năng với cái đã có; đổi thì phải sửa FE, contract và 78 test mà không thêm giá trị. `statusCode` trong body lặp lại HTTP status.

### Frontend

- **Form lọc "chung" = khai báo cấu hình, không suy ra từ entity** — kiểu TypeScript bị xóa khi chạy, nên không thể "ném object `Supplier` vào" rồi tự sinh ô lọc. `FilterBar` nhận mảng `fields` (`keyword` / `select` / `status`); màn hình mới chỉ khai báo 2–3 dòng. Chỉ khai báo những tham số API thực sự hỗ trợ.
- **`type` thay vì `interface` cho bộ lọc** — `type SupplierFilters = {...}` gán được vào `Record<string, ...>`, còn `interface` thì không (interface có thể được mở rộng sau nên TS không cho ngầm định index signature).
- **Lùi trang khi trang hiện tại rỗng** — đang lọc "Đang kinh doanh", trang 3 còn 1 SP, ngừng kinh doanh SP đó → tải lại trang 3 rỗng. `fallbackPageIndex` đưa về trang 2 thay vì báo "không có kết quả".
- **Chống mất dữ liệu form** — `maskClosable={false}` + hỏi lại khi `form.isFieldsTouched()`. Form chưa sửa thì đóng ngay: hỏi thừa làm người dùng quen bấm "Đồng ý" mà không đọc.
- **Một bản ghi – một khung** — gộp khung gán/gỡ riêng vào tab trong `DetailDrawer`; menu `⋯` mở thẳng tab đó. Tránh hai drawer làm cùng một việc.
- **Accessibility của mã bấm được** — `Typography.Link` không có `href` là thẻ `<a>` không Tab tới được; đổi sang `Button type="link"` (thẻ `<button>`). `aria-label` của nút `⋯` kèm mã bản ghi để trình đọc màn hình phân biệt các dòng.

### Câu hỏi ôn tập (để cuối dự án)

1. Vì sao không cho client gửi tên cột tùy ý để lọc, dù làm vậy "chung" hơn?
2. Vì sao không tạo được index trên `lower(unaccent(name))` nhưng tạo được trên `lower(immutable_unaccent(name))`?
3. "Mở lại" gửi PUT với dữ liệu của dòng trong bảng. Nếu người khác vừa sửa tên NCC đó, chuyện gì xảy ra? Cần gì để phát hiện (gợi ý: optimistic locking / `version`)?

---

## Module: Nhà cung cấp (2026-10-16 → 2026-10-17)

### Backend

- **Khóa chính kép + `@EmbeddedId` / `@MapsId`** — `product_supplier` không có cột `id`, khóa chính là `(supplier_id, product_id)`. Gỡ "Cám heo con 25kg" khỏi NCC001 rồi gán lại vẫn chỉ có 1 dòng (đổi `active`). `@MapsId("productId")` cho biết cột `product_id` vừa là một phần khóa vừa là FK tới `product`.
  _Khi nào dùng `id` riêng:_ khi dòng nối có vòng đời riêng hoặc bảng khác cần trỏ tới nó (ví dụ bảng giá theo hợp đồng).
- **Thứ tự cột trong index ghép** — index `(supplier_id, product_id)` phục vụ được "NCC X có SP nào" nhưng **không** phục vụ "SP Y có NCC nào", vì `product_id` đứng sau. Cần thêm index riêng `idx_product_supplier_product`.
- **Flush của Hibernate** — `setActive(false)` chỉ đổi trong bộ nhớ; Hibernate ghi xuống DB khi commit hoặc trước một truy vấn JPA. Test `@Transactional` rollback nên không commit, `JdbcTemplate` đọc thẳng DB vẫn thấy giá trị cũ → phải `entityManager.flush()` trước khi đọc. Sẽ gặp lại khi trộn câu `UPDATE` viết tay (trừ tồn) với JPA.
- **`@EntityGraph` chống N+1** — danh sách 20 liên kết mà không JOIN sẵn `product`/`supplier` sẽ thành 1 + 20 câu SQL.
- **Phụ thuộc giữa module ≠ vị trí URL** — `GET /api/products/{id}/suppliers` đặt code trong module `supplier`, vì `supplier` đã phụ thuộc `product` (kiểm tra SP khi gán). Đặt ở `product` sẽ thành phụ thuộc vòng.
- **Một quy tắc, một chỗ kiểm tra** — gán/gỡ từ phía SP gọi lại API của NCC, không viết endpoint thứ hai → BR-SUP-05/06 chỉ được kiểm tra ở `SupplierService`.
- **Mã lỗi theo contract** — vi phạm nghiệp vụ (gán SP đã ngừng kinh doanh) là 409 `BUSINESS_RULE_VIOLATION`, không phải 400: dữ liệu gửi lên đúng định dạng, chỉ trạng thái hệ thống không cho phép.

### Frontend

- **Tìm kiếm phía server → `filterOption: false`** — gợi ý SP đã được backend lọc; nếu để `Select` lọc thêm trên trình duyệt có thể ẩn mất kết quả đúng. Ngược lại, danh sách NCC tải sẵn (≤ 100) thì lọc trên trình duyệt (`optionFilterProp: 'label'`).
- **Debounce** — chờ 400 ms sau phím cuối mới gọi API, tránh 1 request mỗi phím.
- **`key` để reset state** — `<SupplierProductsPanel key={supplier.id}>`: chuyển từ NCC A sang B, React tạo component mới, không giữ ô tìm kiếm/trang của A.
- **`useCallback` cho hàm tải dữ liệu** — `usePagedList` tải lại mỗi khi hàm đổi; không bọc `useCallback` thì mỗi lần render tạo hàm mới → gọi API liên tục.
- **Khả năng phát hiện chức năng (discoverability)** — chức năng gán SP ban đầu nằm cuối khung chi tiết nên người dùng tưởng chưa có; thêm mục riêng trong menu `⋯` mở thẳng khung gán.

### Công cụ / môi trường

- **Lỗi đỏ trong IDE ≠ lỗi build** — VS Code dùng trình biên dịch Eclipse (ECJ) riêng; ECJ mới + Lombok 1.18.34 báo nhầm "Flexible Constructor Bodies". Tin `mvn compile` trước, rồi mới nghi IDE lệch phiên bản. Sửa: nâng Lombok lên 1.18.42 + "Java: Clean Java Language Server Workspace".
- **Biên dịch tăng dần để lại bytecode cũ** — sửa tạm `WarehouseSearchCriteria` (bỏ `HasActive`) rồi khôi phục: Maven chỉ biên dịch lại file đổi, `WarehouseSpecifications.class` vẫn tham chiếu bản cũ → 82 test lỗi "Constructor threw exception" dù code đúng. `mvn clean verify` hết lỗi. Thấy lỗi khởi tạo bean khó hiểu sau khi đổi kiểu/chữ ký → thử `clean` trước khi sửa code.
- **`mvn clean` lỗi trên Windows** — thư mục `target/` bị IDE/app đang chạy khóa; chạy `mvn verify` không `clean`, hoặc tắt tiến trình đang giữ file.

### Câu hỏi ôn tập (để cuối dự án)

1. Nếu `product_supplier` dùng `id` tự tăng và **không** có UNIQUE `(supplier_id, product_id)`, hai người bấm "Gán" cùng lúc thì sao? Thiết kế hiện tại chặn ở đâu?
2. Vì sao truy vấn "SP Y do NCC nào cung cấp" không dùng được index của khóa chính?
3. Vì sao test `shouldSoftUnlink` fail lần đầu dù code service đúng?

---

## Các module trước (UI polish, 2026-10-16)

### Câu hỏi ôn tập

1. Skeleton loading khác spinner thế nào? Vì sao skeleton làm người dùng cảm thấy trang tải nhanh hơn?
2. Vì sao chỉ hiện thông báo thành công **sau khi** API trả `success: true`, không hiện ngay lúc bấm nút?
