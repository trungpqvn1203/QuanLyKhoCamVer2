# Kiểm kê kho

[← Mục lục kho bài học](../README.md)

## Module: Kiểm kê kho (2026-10-27, migration V22)

- **Ghi phần chênh (delta), không ghi số tuyệt đối.** Sổ sách cám A 120 bao, 8h đếm được 115, 9h bán 10, 10h bấm xác nhận. Nếu "đặt tồn = 115" thì 10 bao vừa bán sống lại trên sổ; ghi "−5" thì tồn = 110 − 5 = 105, đúng. Đây là lý do mỗi dòng lưu riêng `book_quantity` (chụp lúc lập phiếu) và chỉ ghi `counted − book`. Thử đột biến "mỗi lần lưu lại lấy sổ sách mới" → test bán-trong-lúc-đếm ra 115 thay vì 105.
- **Chụp số (snapshot) + cảnh báo thay vì khóa.** Khóa kho trong lúc kiểm thì luôn đúng nhưng kho phải ngừng bán. Chụp số lúc lập phiếu cho phép kho chạy bình thường; cái giá là phải đếm ngay sau khi chụp. Hệ thống bù bằng cảnh báo "SP có nhập/xuất sau lúc chụp" (`stock_transaction.created_at > book_snapshot_at`) và nút chụp lại.
- **Một cột, hai nghĩa có kiểm soát.** `stock_document_item.quantity` vốn là "SL tác động tồn" — với kiểm kê là `|đếm − sổ sách|`, đúng số được cộng/trừ. Không thêm cột mới cho "số điều chỉnh"; CHECK ở DB ràng `quantity = abs(counted − book)` để hai cột không bao giờ lệch nhau.
- **Test rollback phải chạy với transaction thật.** Ca "dòng thiếu không đủ trừ → rollback cả dòng thừa đã ghi" ban đầu viết trong lớp test `@Transactional`: service ném lỗi nhưng transaction bao ngoài của test vẫn còn, thấy tồn 130 thay vì 120. Không phải lỗi code — là test không đo được rollback. Chuyển sang lớp test không `@Transactional` (commit thật) thì đúng; các module trước cũng để ca rollback ở lớp này.
- **Lỗi đã gặp:** heredoc nhiều file trong một lệnh Bash lại hỏng (dấu nháy trong nội dung) → dùng công cụ ghi file từng file.

### Câu hỏi ôn tập (để cuối dự án)

1. Vì sao kiểm kê ghi chênh lệch thay vì đặt tồn bằng số đếm? Cho ví dụ số cụ thể.
2. Chụp số sổ sách lúc nào, khi nào phải chụp lại? Không khóa kho thì đổi lại phải chấp nhận điều gì?
3. Vì sao mỗi kho chỉ được một phiếu đang kiểm? Chuyện gì xảy ra nếu có hai phiếu cùng kiểm cám A?
4. Vì sao test rollback đặt trong lớp `@Transactional` lại cho kết quả sai?
