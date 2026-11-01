# Ô thêm nhanh sản phẩm trên phiếu

[← Mục lục kho bài học](../README.md)

## Module: Thêm nhanh SP cho phiếu bán, nhập, tồn đầu kỳ, kiểm kê (2026-11-01, chỉ frontend)

- **Vấn đề: đếm số thao tác.** Cách cũ để thêm một SP: bấm "Thêm dòng" → click ô Select → gõ tên → chọn → click ô SL → gõ. Tức là phải rời bàn phím sang chuột 3 lần cho mỗi SP, phiếu 15 SP là 45 lần. Cách mới: gõ "cam heo" → Enter → gõ 40 → Enter → gõ SP tiếp theo, không cần chạm chuột. Khi đánh giá trải nghiệm một form nhập liệu, hãy **đếm số lần đổi giữa chuột và bàn phím**. Đây là thước đo đơn giản và rất sát với cảm giác "chậm".
- **Một ô tìm chung thay cho một Select mỗi dòng.** `ProductQuickAdd` (thư mục `components/ui/`) là antd `Select` với `value={null}` cố định. Ô này chỉ dùng để tìm; SP đã chọn nằm trong bảng. Vì giá trị luôn rỗng nên chọn xong ô tự trống để gõ SP tiếp, và chọn lại cùng một SP thì `onSelect` vẫn chạy.
- **Cột SP trong bảng chỉ hiện chữ, nhưng vẫn kiểm tra hợp lệ.** `<Form.Item name={[i, 'productId']}><ProductLabel /></Form.Item>`: Form.Item tự truyền `value` vào component con, nên `ProductLabel` đọc được id để hiện tên, đồng thời rule "Chọn sản phẩm" vẫn chạy. Điều này cần cho phiếu nhập: khi đổi NCC, mọi dòng bị bỏ chọn SP (BR-SUP-08). Ô SP luôn được render nên không lặp lại kiểu lỗi nhấp nháy của phiếu kiểm kê (xem [Kiểm kê](../1-kho-hang/06-kiem-ke.md)).
- **Mỗi SP một dòng: chọn trùng thì nhảy về dòng cũ.** Hàm thuần `resolveQuickAdd` trả về `focus` (SP đã có), `fill` (có dòng chưa có SP thì điền vào đó, giữ nguyên SL/giá đã gõ) hoặc `append`. Phương án thay thế là chọn trùng thì cộng +1 vào SL, hợp với máy quét mã vạch bán lẻ từng món. Kho cám thì nhập 40 bao một lần, cộng +1 không giúp gì mà còn dễ làm sai số. Ở phiếu kiểm kê, `focus` lại chính là việc hay dùng nhất: gõ tên SP để nhảy tới dòng cần nhập số đếm, khỏi phải cuộn tìm trong bảng 80 dòng.
- **Vì sao đặt focus trong `setTimeout`.** `add()` của Form.List chỉ đổi state; ô SL của dòng mới chỉ có trong DOM sau **lần render kế tiếp**. Gọi `form.focusField(['lines', 3, 'quantity'])` ngay lúc đó thì không tìm thấy ô nào. `setTimeout(fn)` đẩy lệnh ra sau khi React render xong (`focusLineField`). Đây là ví dụ cụ thể của việc "đổi state không làm DOM đổi ngay".
- **Tìm không dấu bằng Unicode NFD.** `'Cám'.normalize('NFD')` tách thành `C a ◌́ m`: chữ gốc cộng dấu kết hợp (U+0300–U+036F). Bỏ nhóm dấu đó đi là được "Cam". Riêng `đ` là một ký tự độc lập, NFD không tách được nên phải đổi riêng. Thêm điều kiện "mọi từ đều có mặt", nên gõ "25 heo" vẫn khớp "Cám heo 25kg". Không cần thư viện.
- **Phiếu mới bắt đầu với 0 dòng.** Trước đây phiếu mới có sẵn `lines: [{}]` để người dùng có chỗ chọn SP. Giờ chỗ chọn là ô thêm nhanh, nên một dòng rỗng chỉ gây lỗi "Chọn sản phẩm". Nút xóa cũng không còn bị khóa ở dòng cuối; validator của Form.List vẫn chặn lưu phiếu không có dòng nào.

### Câu hỏi ôn tập (để cuối dự án)

1. Nếu gọi `form.focusField` ngay sau `add()` (không có `setTimeout`) thì chuyện gì xảy ra? Vì sao?
2. Vì sao cột SP vẫn phải nằm trong `Form.Item name=productId` dù chỉ hiện chữ? Bỏ đi thì phiếu nhập sau khi đổi NCC bị lỗi gì?
3. `'đ'.normalize('NFD')` cho ra gì? Vì sao `foldVietnamese` phải đổi `đ` riêng?
4. Khi nào nên chọn "trùng thì cộng +1 SL" thay vì "trùng thì nhảy về dòng cũ"?
