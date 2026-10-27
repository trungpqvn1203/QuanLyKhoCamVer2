# Nhập kho

[← Mục lục kho bài học](../README.md)

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
