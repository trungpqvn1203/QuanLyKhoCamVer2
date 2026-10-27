# Tồn kho đầu kỳ

[← Mục lục kho bài học](../README.md)

## Module: Tồn kho đầu kỳ (2026-10-27, migration V20)

- **Đầu kỳ là chứng từ, không phải con số ghi thẳng vào bảng tồn.** Ví dụ kho Hà Nội có sẵn 500 bao cám A: nếu `UPDATE inventory SET quantity = 500` thì tồn đúng nhưng lịch sử trống — dò "tồn sau" từng bước sẽ không bao giờ ra 500. Lập phiếu `DTK000001` rồi xác nhận thì có một giao dịch IN 0 → 500, tồn và lịch sử khớp nhau (BR-INV-04).
- **Mở rộng thay vì nhân bản.** `stock_document` đã có cột `type` (phiếu nhập `INBOUND`, phiếu bán `OUTBOUND`). Thêm `OPENING_STOCK` là có ngay: khóa phiếu, trạng thái nháp/hoàn tất/đảo, `InventoryService.stockIn/reverse`, trang lịch sử kho. Điều kiện để mở rộng an toàn: **mọi truy vấn cũ đều lọc theo `type`** — đã kiểm tra trước khi làm (phiếu nhập, hàng đang về, công nợ NCC/khách), nếu có truy vấn quên lọc thì phiếu đầu kỳ sẽ lẫn vào báo cáo nhập hàng.
- **Chặn trùng theo "chưa có lịch sử" thay vì theo đợt.** Cặp (cám A, kho Hà Nội) chỉ được khai đầu kỳ khi chưa có giao dịch nào. Một quy tắc này bao luôn: không khai hai lần (lần một tạo lịch sử), quên SP thì khai bổ sung được, và không dùng đầu kỳ để lách kiểm kê (SP đã nhập/bán là có lịch sử). Chặn "mỗi kho một lần" thì không xử lý được trường hợp quên SP.
- **Check-then-act không đủ khi có đồng thời.** Lớp 1 "đọc xem có lịch sử chưa" chạy lúc chưa khóa gì: 5 phiếu cùng chứa cám A bấm xác nhận cùng lúc, cả 5 đều đọc thấy "chưa có" → nếu chỉ có lớp này thì tồn thành 100+200+…+500. Lớp 2 kiểm tra **sau** khi câu upsert đã khóa dòng tồn: `quantity_before` phải bằng 0. Thử bỏ lớp 2 → test đồng thời ra 5 thành công thay vì 1. Đây là cùng một ý với "kiểm tra và trừ tồn trong cùng câu lệnh" của BR-INV-02: điều kiện phải được kiểm khi đang giữ khóa.
- **Đầu kỳ tiền khác đầu kỳ hàng.** Đầu kỳ sổ quỹ là cấu hình (sửa được, có lịch sử) vì sổ quỹ là VIEW cộng dồn từ chứng từ — đổi đầu kỳ chỉ đổi con số cộng thêm. Tồn đầu kỳ làm `inventory` tăng thật, nên phải bất biến và chỉ sửa bằng đảo phiếu.
- **Lỗi đã gặp:**
  - Hai lệnh INSERT sai cố ý trong cùng một test `@Transactional`: lệnh đầu lỗi làm PostgreSQL hủy transaction (`25P02 current transaction is aborted`), lệnh hai không chạy được để kiểm tra CHECK. Mỗi ca vi phạm ràng buộc phải là một test riêng (hoặc dùng savepoint).
  - Test sinh SĐT khách bằng `"09" + nanoTime % 100_000_000` đôi khi ra ít hơn 8 chữ số → vi phạm `ck_customer_phone`, test **chập chờn** (lúc pass lúc lỗi). Phát hiện nhờ mutation test chạy lại nhiều lần. Dữ liệu test phải luôn hợp lệ một cách xác định: dùng bộ đếm `"09%08d"`.

### Câu hỏi ôn tập (để cuối dự án)

1. Vì sao không `UPDATE inventory` thẳng khi khai tồn đầu kỳ? Lịch sử kho sẽ sai ở đâu?
2. Thêm một loại chứng từ vào bảng dùng chung cần kiểm tra điều gì trước để không làm hỏng báo cáo cũ?
3. SP đã nhập 10 bao rồi bán hết 10 (tồn = 0) — vì sao vẫn không cho khai đầu kỳ?
4. Hai lớp chặn trùng: lớp nào đủ khi chỉ một người dùng, lớp nào cần khi nhiều người bấm cùng lúc? Vì sao lớp 2 phải nằm sau câu upsert?
