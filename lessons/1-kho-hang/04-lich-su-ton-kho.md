# Lịch sử biến động tồn kho

[← Mục lục kho bài học](../README.md)

## Module: Lịch sử biến động tồn kho (2026-10-20, không đổi schema)

- **Sổ cái (ledger) trả lời câu hỏi "vì sao"** — `inventory.quantity = 27` chỉ cho biết *bao nhiêu*; `stock_transaction` cho biết *vì sao*. Lọc Cám heo tại Kho HN, đọc từ dưới lên: nhập PN 20 (0→20), bán PX 5 (20→15), nhập PN 12 (15→27), bán PX 3 (27→24), đảo phiếu bán đó (24→27). "Tồn trước" mỗi dòng = "Tồn sau" của dòng ngay dưới; chỗ nào không khớp là dấu hiệu tồn bị sửa ngoài luồng chứng từ. `StockTransactionApiTest` kiểm đúng điều này trên luồng phiếu thật.
- **Tồn trước/sau là của MỘT kho** — khi chỉ lọc SP mà không lọc kho, các dòng của Kho HN và Kho HP xen kẽ nhau nên chuỗi trước/sau không nối liền. Muốn dò tồn phải lọc cả SP lẫn kho.
- **Sắp theo `id`, không theo `created_at`** — `@CreationTimestamp`/`now()` cho mọi dòng trong cùng một transaction cùng một thời điểm: hoàn tất phiếu 2 dòng → 2 giao dịch trùng `created_at`, sắp theo thời gian thì thứ tự không xác định. `id` (IDENTITY) luôn tăng theo đúng thứ tự ghi.
- **Ngày ghi sổ ≠ ngày chứng từ** — phiếu nhập ngày chứng từ 15/10 nhưng hoàn tất 20/10 thì tồn tăng vào 20/10. Bộ lọc ngày của trang lịch sử dùng `createdAt` (thời điểm tồn thực sự đổi), cột "Chứng từ" vẫn hiện ngày chứng từ để đối chiếu.
- **`@EntityGraph` với chuỗi quan hệ *-to-one** — mỗi dòng cần SP, kho, dòng chứng từ → chứng từ → NCC/khách. Toàn là quan hệ *-to-one nên JOIN FETCH không nhân bản dòng, phân trang vẫn làm trong SQL (`LIMIT`). Nếu fetch một collection (`items`) cùng phân trang, Hibernate phải phân trang trong bộ nhớ (cảnh báo HHH90003004).
- **Lấy id từ proxy LAZY không tốn truy vấn** — `getReversalOf().getId()`: Hibernate đã biết khóa ngoại nên trả id ngay, không SELECT bản ghi gốc.
- **Mở rộng helper chung thay vì viết riêng** — `keywordContains` trước chỉ nhận thuộc tính trực tiếp (`root.get("code")`); tìm theo mã phiếu cần `documentItem.document.code`. Sửa helper dùng chung `path()` (đã có cho `equal`) kèm test, theo quy ước search-pattern.
- **Index hiện có** — `idx_stock_transaction_product_warehouse (product_id, warehouse_id, created_at)` phục vụ lọc theo SP (+ kho). Lọc chỉ theo ngày hoặc chỉ theo kho chưa có index riêng; chưa thêm vì dữ liệu còn nhỏ — kiểm bằng `EXPLAIN ANALYZE` ở mục tối ưu Tuần 3.

### Câu hỏi ôn tập (để cuối dự án)

1. Vì sao chỉ lọc theo SP (không lọc kho) thì cột "Tồn trước" không nối liền với "Tồn sau" của dòng bên dưới?
2. Hai giao dịch của cùng một phiếu có `created_at` giống hệt nhau — vì sao? Hệ quả với việc sắp xếp?
3. Nếu ai đó chạy `UPDATE inventory SET quantity = 30` trực tiếp trên DB, trang lịch sử sẽ lộ ra điều đó ở đâu?
