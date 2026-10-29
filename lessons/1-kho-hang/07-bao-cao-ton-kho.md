# Báo cáo tồn kho và trang tổng quan

[← Mục lục kho bài học](../README.md)

## Module: Tổng quan + báo cáo tồn (2026-10-29)

- **Báo cáo đọc lại sổ, không lưu thêm số liệu.** Mọi con số được tính lúc mở báo cáo từ các bảng đã có: `inventory` (tồn hiện tại), `stock_transaction` (sổ biến động bất biến), `stock_document` (phiếu bán). Không cần bảng "số tồn từng ngày" hay "doanh số từng ngày", vì những bảng như vậy dễ lệch với sổ gốc khi có phiếu đảo hoặc sửa. Đây là lợi ích của quy tắc cứng số 2 (lịch sử bất biến): sổ biến động đủ tin cậy để dựng lại tồn ở bất kỳ thời điểm nào. Chỉ khi dữ liệu lên hàng triệu dòng mà báo cáo chậm mới cần bảng tổng hợp (snapshot) theo ngày.
- **Nhập – xuất – tồn tính bằng một câu SQL.** Mỗi SP: `SUM(CASE WHEN created_at < đầu kỳ ...)` ra tồn đầu, phần trong kỳ tách thành nhập và xuất. Tồn cuối = đầu + nhập − xuất. Test kiểm chứng tồn cuối hôm nay đúng bằng `SUM(inventory.quantity)`: hai nguồn độc lập (sổ biến động và bảng tồn) phải khớp nhau, đây cũng là một bài kiểm tra quy tắc cứng số 2.
- **Giao dịch đảo bù vào cột gốc.** Đảo một phiếu nhập sinh giao dịch `OUT`. Nếu cộng nó vào cột Xuất thì báo cáo ghi "nhập 4, xuất 4" cho một phiếu nhập nhầm, làm khó đối chiếu với thủ kho. Cách xử lý: giao dịch có `reversal_of_id` thì **trừ** vào cột của giao dịch gốc. Chiều của giao dịch đảo luôn ngược với giao dịch gốc (`OUT` đảo `IN`), nên không cần JOIN sang dòng gốc.
- **Theo ngày ghi sổ hay ngày chứng từ?** Báo cáo dùng `created_at` của giao dịch (thời điểm hoàn tất hoặc đảo), không dùng `document_date`. Lý do: phiếu có thể ghi ngày chứng từ là hôm qua nhưng hôm nay mới hoàn tất; nếu tính theo ngày chứng từ thì "tồn cuối hôm qua" sẽ thay đổi sau khi đã in báo cáo, tức là sửa ngược quá khứ. Ngược lại, **doanh số** dùng ngày bán (`document_date`), vì kế toán chốt doanh số theo ngày trên phiếu.
- **Không cộng số lượng khác đơn vị.** "50 bao + 40 kg = 90" là con số vô nghĩa. Dòng tổng của báo cáo chỉ cộng **tấn**. Lợi ích của việc bắt buộc khối lượng tịnh (BR-PRD-06) thể hiện ở đây: mọi mặt hàng quy được về cùng một đơn vị để so sánh.
- **Phân quyền trang tổng quan theo thẻ.** Backend `/api/reports/dashboard` chỉ trả số mà mọi vai trò vốn đã xem được (tồn, phiếu bán). Công nợ và sổ quỹ thì frontend gọi đúng API sẵn có của module đó, vốn đã chặn theo vai trò. Không phải viết lại phân quyền trong báo cáo, và không lộ số quỹ cho nhân viên bán hàng.
- **Tải lười thư viện nặng.** Recharts nặng khoảng 103 KB gzip. Nếu import thẳng thì mọi trang (Sản phẩm, Kho…) đều phải tải. Dùng `React.lazy(() => import('./SalesChart'))` + `Suspense` thì chỉ trang Tổng quan mới tải; đã đo bằng `npm run build`: bundle chính giảm từ 2,03 MB về 1,67 MB.
- **"Đang tải" suy ra từ dữ liệu, không lưu cờ riêng.** Kết quả lưu kèm khóa của bộ lọc đã gửi (`{ key, data }`); `loading = result.key !== khóa hiện tại`. Đổi bộ lọc thì tự thành "đang tải" mà không cần gọi `setLoading(true)` trong `useEffect` (lint chặn setState đồng bộ trong effect), và kết quả cũ về muộn không đè lên bộ lọc mới.

### Lỗi đã gặp: text block Java cắt khoảng trắng đầu dòng

- Điều kiện lọc từ khóa được ghép bằng text block `"""\n AND (...)"""`. Java bỏ khoảng trắng chung ở đầu các dòng (incidental whitespace), nên khi lọc **cả** danh mục lẫn từ khóa thì SQL thành `p.category_id = :categoryIdAND (...)`. Spring báo "No value for parameter 'categoryIdAND'". Test có lọc kết hợp đã bắt được lỗi này. Sửa: dùng chuỗi thường bắt đầu bằng dấu cách. Bài học: ghép SQL động thì phải có test cho **tổ hợp** các bộ lọc, không chỉ từng bộ lọc riêng.

### Câu hỏi ôn tập (để cuối dự án)

1. Vì sao không cần bảng lưu "tồn cuối mỗi ngày" để làm báo cáo nhập – xuất – tồn? Khi nào mới cần?
2. Phiếu nhập 4 bao rồi đảo trong cùng kỳ: báo cáo hiện gì ở cột Nhập / Xuất? Vì sao không để giao dịch đảo vào cột Xuất?
3. Báo cáo tồn dùng ngày ghi sổ, doanh số dùng ngày bán. Cho ví dụ hai cách cho ra số khác nhau.
4. Vì sao thẻ "Tiền mặt" trên tổng quan gọi API sổ quỹ chứ không nằm trong `/api/reports/dashboard`?
5. Nêu một lỗi có thể xảy ra khi ghép chuỗi SQL động, và cách test để phát hiện.
