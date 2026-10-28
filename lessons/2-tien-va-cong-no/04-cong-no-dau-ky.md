# Công nợ đầu kỳ khách / NCC

[← Mục lục kho bài học](../README.md)

## Module: Công nợ đầu kỳ khách / NCC (2026-10-27, migration V21)

- **Đặt dữ liệu mới ở chỗ các cơ chế cũ đã trỏ tới.** Trại gà Đồng Tâm nợ cũ 7,5 triệu. Bảng thu tiền `sale_payment` có `document_id → stock_document`, nên khoản nợ cũ là một dòng `stock_document` (type `OPENING_CUSTOMER_DEBT`, không kho, không dòng hàng) thì được ngay: thu TM + CK, hoàn khoản thu nhầm, chặn thu vượt (CHECK `paid_amount ≤ total_amount`), trang công nợ, và sổ quỹ hiện "DKH000001 +2.500.000" mà không sửa VIEW. Đánh đổi phải trả: `warehouse_id` thành nullable → bù bằng CHECK `(là nợ đầu kỳ) = (không có kho)`, để phiếu nhập/bán vẫn không thể thiếu kho.
- **UNIQUE một phần (partial unique index).** `CREATE UNIQUE INDEX … ON stock_document (customer_id) WHERE type = 'OPENING_CUSTOMER_DEBT' AND status = 'COMPLETED'`: chỉ những dòng thỏa `WHERE` mới bị so trùng. Nhờ vậy "mỗi khách một khoản còn hiệu lực" nằm ngay ở DB, khoản đã đảo không chặn việc nhập lại, và các loại chứng từ khác không bị ảnh hưởng. Tồn đầu kỳ (V20) không làm được như vậy vì điều kiện nằm ở bảng khác (`stock_transaction`).
- **Kiểm tra trước để báo lỗi dễ hiểu, UNIQUE để chặn thật.** Service tìm khách đã có nợ đầu kỳ và báo "Đã có nợ đầu kỳ cho 'Trại gà Đồng Tâm'". Nhưng hai người bấm cùng lúc đều qua bước kiểm tra — UNIQUE chặn người sau. `saveAllAndFlush` để lỗi nổ ra ngay trong service (thành 409), không đợi tới lúc commit.
- **Một controller cha, hai URL.** Khách và NCC dùng chung `OpeningDebtController` (abstract) + hai lớp con `@RequestMapping` khác nhau. Giữ URL riêng để SecurityConfig phân quyền theo URL như mọi module: ghi nhận/đảo chỉ quản trị, thu nợ cũ khách theo quyền bán hàng, trả nợ cũ NCC theo quyền quản lý kho.
- **Lỗi đã gặp:**
  - `'OPENING_CUSTOMER_DEBT'` dài 21 ký tự, cột `type` là `VARCHAR(20)` → INSERT lỗi, bị bắt chung thành 409 "vi phạm ràng buộc". Tên hằng số mới phải đối chiếu với độ dài cột cũ.
  - Sửa file migration **sau khi đã chạy** (thêm câu nới cột vào V21) → Flyway báo checksum không khớp, cả bộ test không khởi động được. Backend dev chạy trong IDE cũng đã tự áp V21 cũ vào DB dev. Vì V21 chưa commit, đã gỡ tay V21 khỏi DB test và DB dev rồi để Flyway chạy lại. **Migration đã lên server thì tuyệt đối không sửa** — phải viết migration mới.
  - Test quy ước (`SearchConventionTest`) đòi mỗi `XxxSearchCriteria` có `XxxSpecifications.from(criteria)` một tham số → đưa `party` vào criteria, controller gán theo URL bằng `withParty`.
  - Dọn dữ liệu test đồng thời phải tắt trigger bất biến của `sale_payment` (trong transaction), như các test đồng thời khác.

### Câu hỏi ôn tập (để cuối dự án)

1. Vì sao khoản nợ cũ đặt trong `stock_document` thay vì bảng riêng? Cái giá phải trả là gì và bù lại bằng ràng buộc nào?
2. Partial unique index khác UNIQUE thường thế nào? Vì sao phải có `status = 'COMPLETED'` trong `WHERE`?
3. Đã có kiểm tra trùng trong service rồi, vì sao vẫn cần UNIQUE ở DB?
4. Migration đã chạy rồi mà cần sửa thì làm gì — khi nào được sửa file cũ, khi nào bắt buộc viết file mới?
